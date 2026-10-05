package sh.kss.finmgr.lib.operation;

import org.javamoney.moneta.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.validation.BeanPropertyBindingResult;
import sh.kss.finmgr.lib.FinmgrTest;
import sh.kss.finmgr.lib.entity.AccountType;
import sh.kss.finmgr.lib.entity.Portfolio;
import sh.kss.finmgr.lib.entity.Quantity;
import sh.kss.finmgr.lib.entity.Run;
import sh.kss.finmgr.lib.entity.transaction.InvestmentTransaction;
import sh.kss.finmgr.lib.entity.transaction.InvestmentTransactionValidator;
import sh.kss.finmgr.lib.service.TransactionServiceImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecimalQuantityCostBasisTest extends FinmgrTest {
    private final TransactionServiceImpl service = new TransactionServiceImpl();
    private final AverageCostBasis operation = new AverageCostBasis();

    DecimalQuantityCostBasisTest() {
        operation.setTransactionService(service);
    }

    private Portfolio closePosition(String amount) {
        BigDecimal quantity = new BigDecimal(amount);
        InvestmentTransaction buy = BUY_VTI
            .withQuantity(new Quantity(quantity))
            .withGrossAmount(Money.of(quantity.multiply(BigDecimal.valueOf(-100)), USD))
            .withCommission(ZERO_USD)
            .withNetAmount(Money.of(quantity.multiply(BigDecimal.valueOf(-100)), USD));
        InvestmentTransaction sell = SELL_VTI
            .withQuantity(new Quantity(quantity.negate()))
            .withGrossAmount(buy.getGrossAmount().negate())
            .withCommission(ZERO_USD)
            .withNetAmount(buy.getNetAmount().negate());
        for (InvestmentTransaction transaction : List.of(buy, sell)) {
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(transaction, "transaction");
            new InvestmentTransactionValidator().validate(transaction, errors);
            assertFalse(errors.hasErrors(), errors.toString());
        }
        return Run.process(Portfolio.EMPTY_NON_REGISTERED, List.of(operation), List.of(buy, sell));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.0", "100.00", "10.50", "0.125"})
    void fullySoldPositionIsRemovedAtEveryScale(String amount) {
        Portfolio portfolio = closePosition(amount);
        assertFalse(portfolio.getHoldings().get(AccountType.NON_REGISTERED).getSecurities().contains(VTI));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.0", "100.00", "10.50", "0.125"})
    void fullySoldPositionReturnsZeroCostBasisAtEveryScale(String amount) {
        Portfolio portfolio = closePosition(amount);
        assertEquals(ZERO_USD, service.getACB(portfolio, AccountType.NON_REGISTERED, VTI));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.0", "100.00", "10.50", "0.125"})
    void partialSalePreservesCostBasis(String amount) {
        BigDecimal quantity = new BigDecimal(amount);
        InvestmentTransaction buy = BUY_VTI
            .withQuantity(new Quantity(quantity))
            .withGrossAmount(Money.of(quantity.multiply(BigDecimal.valueOf(-100)), USD))
            .withCommission(ZERO_USD)
            .withNetAmount(Money.of(quantity.multiply(BigDecimal.valueOf(-100)), USD));
        InvestmentTransaction sell = SELL_VTI
            .withQuantity(new Quantity(quantity.divide(BigDecimal.valueOf(-2))))
            .withGrossAmount(Money.of(quantity.multiply(BigDecimal.valueOf(50)), USD))
            .withCommission(ZERO_USD)
            .withNetAmount(Money.of(quantity.multiply(BigDecimal.valueOf(50)), USD));
        Portfolio portfolio = Run.process(Portfolio.EMPTY_NON_REGISTERED, List.of(operation), List.of(buy, sell));
        assertTrue(portfolio.getHoldings().get(AccountType.NON_REGISTERED).getSecurities().contains(VTI));
        assertEquals(Money.of(100, USD), service.getACB(portfolio, AccountType.NON_REGISTERED, VTI));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.0", "100.00", "10.50", "0.125"})
    void repurchaseAfterClosureStartsFreshCostBasis(String amount) {
        Portfolio portfolio = operation.process(closePosition(amount), BUY_VTI_HIGHER_PRICE);
        assertTrue(portfolio.getHoldings().get(AccountType.NON_REGISTERED).getSecurities().contains(VTI));
        assertEquals(Money.of(105.05, USD), service.getACB(portfolio, AccountType.NON_REGISTERED, VTI));
    }
}
