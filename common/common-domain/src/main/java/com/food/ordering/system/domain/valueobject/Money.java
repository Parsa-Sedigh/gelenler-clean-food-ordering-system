package com.food.ordering.system.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class Money {
    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isGreaterThanZero() {
        /* Note: we used compareTo() method instead of equals() method on the big decimal object. If you used equals() method and if
        you have a big decimal with decimal points like 0.00 and you use 0 integer in the comparison(> 0), it will not return the
        correct result. So by using compareTo() method, we make sure that the comparison return the correct result, whether my
        value has decimal points or not. So it's a best practice to use compareTo() method for this type of comparisons in the BigDecimal ops.
        So: but the correct result is(assuming amount is 0.00): amount.compareTo(BigDecimal.ZERO) > 0, this would return false.*/
        return this.amount != null && this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isGreaterThan(Money money) {
        return this.amount != null && this.amount.compareTo(money.getAmount()) > 0;
    }

    public Money add(Money money) {
        return new Money(setScale(this.amount.add(money.getAmount())));
    }

    public Money subtract(Money money) {
        return new Money(setScale(this.amount.subtract(money.getAmount())));
    }

    public Money multiply(int multiplier) {
        return new Money(setScale(this.amount.multiply(new BigDecimal(multiplier))));
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    private BigDecimal setScale(BigDecimal input) {
        /* With scale 2, the number of digits after decimal point is 2, e.g. 10.75 or 500.80 .
        When you setScale(), after each BigDecimal op, the result will be rounded to that scale. Because of the nature of
        floating point arithmetic, not all calculations will give smooth results. Some numbers in binary system simply cannot be shown
        exactly, as in the decimal numbers. For example, think about 1 divided by 3. This cannot be shown with an exact number using
        decimal point, it has a repeating 3. There are also numbers in binary system with this nature. For example 7 divided by 10 in
        binary system, it's actually: 111/1010 = 0.10110011001100... . The 1100 is the repeating part. Because of that behavior, in java,
        resulting value is chosen with the most accurate result using the available bits. In other words, java uses available bits to
        represent repeating fractional numbers. For example:
        Double 0.7 = 111/1010 = 0.10110011001100...11(52 bit) -> bits after 52nd bit are truncated.

        When you keep adding numbers, the total error will keep getting bigger.

        To minimize the total error, we use RoundingMode.HALF_EVEN which statistically minimizes the cumulative error.*/
        return input.setScale(2, RoundingMode.HALF_EVEN);
    }
}
