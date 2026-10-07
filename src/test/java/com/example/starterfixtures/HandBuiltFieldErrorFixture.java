package com.example.starterfixtures;

import com.example.starter.ApiFieldCode;
import com.example.starter.platform.error.FieldError;
import java.util.List;

/**
 * The negative control for {@code BanListArchTest.fieldErrorsAreBuiltFromTheirParams}: {@code literal} and
 * {@code literalParameter} build a field error through a constructor with a wire-code string, and must be reported;
 * {@code typed} builds it through {@code FieldError.of} from its params record, and must not be.
 */
class HandBuiltFieldErrorFixture {

    FieldError literal() {
        return new FieldError("/name", "validation.unknown-field", new ApiFieldCode.UnknownField(List.of()), null);
    }

    FieldError literalParameter() {
        return new FieldError(
                null, FieldError.In.QUERY, "name", "validation.required", new ApiFieldCode.Required(), null);
    }

    FieldError typed() {
        return FieldError.of("/name", new ApiFieldCode.UnknownField(List.of()));
    }
}
