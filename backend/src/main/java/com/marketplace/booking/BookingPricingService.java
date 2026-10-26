package com.marketplace.booking;

import com.marketplace.exception.ConflictException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class BookingPricingService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.03");
    private static final BigDecimal OWNER_COMMISSION_RATE = new BigDecimal("0.12");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");

    public Quote calculate(
            Instant startTime,
            Instant endTime,
            BigDecimal dailyRate,
            BigDecimal weeklyRate,
            BigDecimal deliveryCharge,
            BigDecimal securityDeposit,
            int minimumRentalDays
    ) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ConflictException("Rental end time must be later than its start time.");
        }
        Duration duration = Duration.between(startTime, endTime);
        long fullDays = duration.toDays();
        long rentalDaysLong = fullDays + (duration.minusDays(fullDays).isZero() ? 0 : 1);
        if (rentalDaysLong > Integer.MAX_VALUE) {
            throw new ConflictException("Rental period is too long to quote.");
        }
        int rentalDays = (int) rentalDaysLong;
        if (rentalDays < minimumRentalDays) {
            throw new ConflictException("Rental period is shorter than the equipment minimum.");
        }

        BigDecimal rentalAmount;
        if (weeklyRate != null && rentalDays >= 7) {
            int fullWeeks = rentalDays / 7;
            int remainingDays = rentalDays % 7;
            rentalAmount = weeklyRate.multiply(BigDecimal.valueOf(fullWeeks))
                    .add(dailyRate.multiply(BigDecimal.valueOf(remainingDays)));
        } else {
            rentalAmount = dailyRate.multiply(BigDecimal.valueOf(rentalDays));
        }
        rentalAmount = money(rentalAmount);
        BigDecimal delivery = money(deliveryCharge);
        BigDecimal bookingValue = rentalAmount.add(delivery);
        BigDecimal fee = money(bookingValue.multiply(PLATFORM_FEE_RATE));
        BigDecimal ownerCommission = money(rentalAmount.multiply(OWNER_COMMISSION_RATE));
        BigDecimal taxableAmount = bookingValue.add(fee);
        BigDecimal tax = money(taxableAmount.multiply(TAX_RATE));
        BigDecimal deposit = money(securityDeposit);
        BigDecimal total = rentalAmount.add(delivery).add(fee).add(tax).add(deposit);

        return new Quote(rentalDays, rentalAmount, delivery, fee, ownerCommission,
                rentalAmount.subtract(ownerCommission).add(delivery), tax, deposit, money(total));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record Quote(
            int rentalDays,
            BigDecimal baseRentalAmount,
            BigDecimal deliveryCharge,
            BigDecimal platformFeeAmount,
            BigDecimal ownerCommissionAmount,
            BigDecimal ownerEarningsEstimated,
            BigDecimal taxAmount,
            BigDecimal securityDepositAmount,
            BigDecimal totalPayableAmount
    ) {
        public BookingPricing toResponse() {
            return new BookingPricing("INR", rentalDays, baseRentalAmount, deliveryCharge,
                    platformFeeAmount, taxAmount, securityDepositAmount, totalPayableAmount);
        }
    }
}
