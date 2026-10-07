package com.example.starterfixtures;

import com.example.starter.ApiFieldCode;
import com.example.starter.platform.error.FieldError;

/**
 * The negative control for {@code BanListArchTest.fieldErrorsAreBuiltFromTheirParams}: {@code literal} builds a
 * field error through its canonical constructor with a wire-code string, and must be reported; {@code typed}
 * builds it through {@code FieldError.of} from its params record, and must not be.
 */
class HandBuiltFieldErrorFixture {

    FieldError literal() {
        return new FieldError("/name", "validation.unknown-field", new ApiFieldCode.UnknownField(), null);
    }

    FieldError typed() {
        return FieldError.of("/name", new ApiFieldCode.UnknownField());
    }
}
