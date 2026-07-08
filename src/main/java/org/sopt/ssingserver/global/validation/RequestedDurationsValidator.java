package org.sopt.ssingserver.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestedDurationsValidator implements ConstraintValidator<ValidRequestedDurations, Collection<Integer>> {

    private Set<Integer> allowedValues;
    private String notAllowedMessage;
    private String duplicatedMessage;

    @Override
    public void initialize(ValidRequestedDurations constraintAnnotation) {
        allowedValues = Arrays.stream(constraintAnnotation.allowedValues())
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
        notAllowedMessage = constraintAnnotation.notAllowedMessage();
        duplicatedMessage = constraintAnnotation.duplicatedMessage();
    }

    @Override
    public boolean isValid(Collection<Integer> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null)) {
            return true;
        }

        if (!allowedValues.containsAll(values)) {
            addViolation(context, notAllowedMessage);
            return false;
        }

        if (new HashSet<>(values).size() != values.size()) {
            addViolation(context, duplicatedMessage);
            return false;
        }

        return true;
    }

    private void addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
