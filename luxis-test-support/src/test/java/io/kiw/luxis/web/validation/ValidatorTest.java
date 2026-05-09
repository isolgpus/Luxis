package io.kiw.luxis.web.validation;

import io.kiw.luxis.result.Result;
import io.kiw.luxis.web.http.ErrorMessageResponse;
import io.kiw.luxis.web.http.ErrorStatusCode;
import io.kiw.luxis.web.http.HttpErrorResponse;
import io.kiw.luxis.web.http.HttpSession;
import io.kiw.luxis.web.pipeline.ErrorMessageResponseMapper;
import io.kiw.luxis.web.test.internal.StubRequestContext;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ValidatorTest {

    private ErrorMessageResponseMapper<HttpErrorResponse> errorMessageResponseMapper = (err, cause) -> new HttpErrorResponse(err, ErrorStatusCode.UNPROCESSABLE_ENTITY);

    private static class Body {
        String name;
        String email;
        Integer age;
        Address address;
        List<Address> addresses;

        Body(String name, String email, Integer age, Address address) {
            this.name = name;
            this.email = email;
            this.age = age;
            this.address = address;
        }

        Body(String name, String email, Integer age, Address address, List<Address> addresses) {
            this.name = name;
            this.email = email;
            this.age = age;
            this.address = address;
            this.addresses = addresses;
        }
    }

    private static class Address {
        String city;
        String zip;

        Address(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }
    }

    private Validator<Body, ErrorMessageResponse> validator(Body body) {
        return new Validator<>(body, "", (err, a) -> err);
    }

    private HttpValidator<Body> validatorWithHttp(Body body, Map<String, String> queryParams, Map<String, String> pathParams) {
        StubRequestContext ctx = new StubRequestContext("{}", queryParams, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        ctx.setPathParams(pathParams);
        return new HttpValidator<>(body, new HttpSession(ctx), "", errorMessageResponseMapper);
    }

    // --- required ---

    @Test
    public void shouldFailRequiredValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "a@b.com", 1, null));
        v.field("name", r -> r.name).required();
        assertError(v, "name", "must not be blank");
    }

    @Test
    public void shouldFailRequiredValidationWhenBlank() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("  ", "a@b.com", 1, null));
        v.field("name", r -> r.name).required();
        assertError(v, "name", "must not be blank");
    }

    @Test
    public void shouldPassRequiredValidationWhenNonBlank() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("Alice", "a@b.com", 1, null));
        v.field("name", r -> r.name).required();
        assertNoErrors(v);
    }

    // --- minLength ---

    @Test
    public void shouldFailMinLengthWhenBelowMin() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("Al", null, null, null));
        v.field("name", r -> r.name).minLength(3);
        assertError(v, "name", "must be at least 3 characters");
    }

    @Test
    public void shouldPassMinLengthAtMin() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("Ali", null, null, null));
        v.field("name", r -> r.name).minLength(3);
        assertNoErrors(v);
    }

    @Test
    public void shouldSkipMinLengthValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.field("name", r -> r.name).minLength(3);
        assertNoErrors(v);
    }

    // --- maxLength ---

    @Test
    public void shouldFailMaxLengthWhenAboveMax() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("Alicia", null, null, null));
        v.field("name", r -> r.name).maxLength(3);
        assertError(v, "name", "must be at most 3 characters");
    }

    @Test
    public void shouldPassMaxLengthAtMax() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("Ali", null, null, null));
        v.field("name", r -> r.name).maxLength(3);
        assertNoErrors(v);
    }

    // --- email ---

    @Test
    public void shouldFailEmailValidationWithoutAt() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "notanemail", null, null));
        v.field("email", r -> r.email).email();
        assertError(v, "email", "must be a valid email address");
    }

    @Test
    public void shouldFailEmailValidationWithSpaces() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "a b@c.com", null, null));
        v.field("email", r -> r.email).email();
        assertError(v, "email", "must be a valid email address");
    }

    @Test
    public void shouldPassEmailValidationOnValidEmail() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "user@example.com", null, null));
        v.field("email", r -> r.email).email();
        assertNoErrors(v);
    }

    @Test
    public void shouldSkipEmailValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.field("email", r -> r.email).email();
        assertNoErrors(v);
    }

    @Test
    public void shouldSkipEmailValidationWhenBlank() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "  ", null, null));
        v.field("email", r -> r.email).email();
        assertNoErrors(v);
    }

    // --- matches ---

    @Test
    public void shouldFailMatchesValidationWhenNoMatch() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("abc", null, null, null));
        v.field("name", r -> r.name).matches("[0-9]+");
        assertError(v, "name", "must match pattern: [0-9]+");
    }

    @Test
    public void shouldPassMatchesValidationWhenMatched() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body("123", null, null, null));
        v.field("name", r -> r.name).matches("[0-9]+");
        assertNoErrors(v);
    }

    // --- numeric min/max ---

    @Test
    public void shouldFailMinValidationWhenBelowMin() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, -1, null));
        v.field("age", r -> r.age).min(0);
        assertError(v, "age", "must be at least 0.0");
    }

    @Test
    public void shouldPassMinValidationAtMin() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, 0, null));
        v.field("age", r -> r.age).min(0);
        assertNoErrors(v);
    }

    @Test
    public void shouldFailMaxValidationWhenAboveMax() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, 200, null));
        v.field("age", r -> r.age).max(150);
        assertError(v, "age", "must be at most 150.0");
    }

    @Test
    public void shouldPassMaxValidationAtMax() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, 150, null));
        v.field("age", r -> r.age).max(150);
        assertNoErrors(v);
    }

    @Test
    public void shouldFailNumericRequiredValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.field("age", r -> r.age).required();
        assertError(v, "age", "must not be blank");
    }

    // --- multiple errors accumulate ---

    @Test
    public void shouldAccumulateErrorsForMultipleRulesOnSameField() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.field("name", r -> r.name).required();
        v.field("email", r -> r.email).required();
        assertEquals(2, v.errors.size());
        assertTrue(v.errors.containsKey("name"));
        assertTrue(v.errors.containsKey("email"));
    }

    @Test
    public void shouldRecordBothErrorsWhenRequiredAndFormatFail() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, "bad", null, null));
        v.field("name", r -> r.name).required();
        v.field("email", r -> r.email).required().email();
        List<String> emailErrors = v.errors.get("email");
        assertNotNull(emailErrors);
        assertEquals("must be a valid email address", emailErrors.get(0));
    }

    // --- nested body field ---

    @Test
    public void shouldPrefixErrorKeysForNestedField() {
        Body body = new Body(null, null, null, new Address(null, "bad"));
        Validator<Body, ErrorMessageResponse> v = validator(body);
        v.field("address", r -> r.address, a -> {
            a.field("city", x -> x.city).required();
            a.field("zip", x -> x.zip).matches("[0-9]{5}");
        });
        assertTrue(v.errors.containsKey("address.city"));
        assertTrue(v.errors.containsKey("address.zip"));
    }

    @Test
    public void shouldSkipNestedFieldValidationWhenNull() {
        Body body = new Body(null, null, null, null);
        Validator<Body, ErrorMessageResponse> v = validator(body);
        v.field("address", r -> r.address, a -> {
            a.field("city", x -> x.city).required();
        });
        assertNoErrors(v);
    }

    @Test
    public void shouldPassNestedFieldValidationWhenValid() {
        Body body = new Body(null, null, null, new Address("NYC", "12345"));
        Validator<Body, ErrorMessageResponse> v = validator(body);
        v.field("address", r -> r.address, a -> {
            a.field("city", x -> x.city).required();
            a.field("zip", x -> x.zip).required().matches("[0-9]{5}");
        });
        assertNoErrors(v);
    }

    // --- query param ---

    @Test
    public void shouldFailQueryParamValidationWhenMissing() {
        HttpValidator<Body> v = validatorWithHttp(new Body(null, null, null, null),
                Collections.emptyMap(), Collections.emptyMap());
        v.queryParam("page").required();
        assertError(v, "page", "must not be blank");
    }

    @Test
    public void shouldFailQueryParamValidationOnPatternMismatch() {
        HttpValidator<Body> v = validatorWithHttp(new Body(null, null, null, null),
                Map.of("page", "abc"), Collections.emptyMap());
        v.queryParam("page").matches("[0-9]+");
        assertError(v, "page", "must match pattern: [0-9]+");
    }

    @Test
    public void shouldPassQueryParamValidationWhenValid() {
        HttpValidator<Body> v = validatorWithHttp(new Body(null, null, null, null),
                Map.of("page", "5"), Collections.emptyMap());
        v.queryParam("page").required().matches("[0-9]+");
        assertNoErrors(v);
    }

    // --- path param ---

    @Test
    public void shouldFailPathParamValidationWhenMissing() {
        HttpValidator<Body> v = validatorWithHttp(new Body(null, null, null, null),
                Collections.emptyMap(), Collections.emptyMap());
        v.pathParam("userId").required();
        assertError(v, "userId", "must not be blank");
    }

    @Test
    public void shouldPassPathParamValidationWhenPresent() {
        HttpValidator<Body> v = validatorWithHttp(new Body(null, null, null, null),
                Collections.emptyMap(), Map.of("userId", "42"));
        v.pathParam("userId").required().matches("[0-9]+");
        assertNoErrors(v);
    }

    // --- toResult ---

    @Test
    public void shouldReturnSuccessFromToResultWhenNoErrors() {
        Body body = new Body("Alice", "a@b.com", 25, null);
        HttpValidator<Body> v = new HttpValidator<>(body, null, "", errorMessageResponseMapper);
        v.field("name", r -> r.name).required();
        Result<HttpErrorResponse, Body> result = v.toResult();
        boolean[] isSuccess = {false};
        result.consume(err -> {
        }, val -> isSuccess[0] = true);
        assertTrue(isSuccess[0]);
    }

    @Test
    public void shouldReturnErrorFromToResultWhenErrors() {
        HttpValidator<Body> v = new HttpValidator<>(new Body(null, null, null, null), null, "", errorMessageResponseMapper);
        v.field("name", r -> r.name).required();
        Result<HttpErrorResponse, Body> result = v.toResult();
        boolean[] isError = {false};
        int[] statusCode = {0};
        result.consume(err -> {
            isError[0] = true;
            statusCode[0] = err.statusCode();
        }, val -> {
        });
        assertTrue(isError[0]);
        assertEquals(422, statusCode[0]);
    }

    // --- listField ---

    @Test
    public void shouldFailListFieldRequiredValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.listField("addresses", r -> r.addresses).required();
        assertError(v, "addresses", "must not be null");
    }

    @Test
    public void shouldFailListFieldMinSizeWhenBelowMin() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null, Collections.emptyList()));
        v.listField("addresses", r -> r.addresses).minSize(1);
        assertError(v, "addresses", "must have at least 1 items");
    }

    @Test
    public void shouldFailListFieldMaxSizeWhenAboveMax() {
        List<Address> three = Arrays.asList(new Address("A", "11111"), new Address("B", "22222"), new Address("C", "33333"));
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null, three));
        v.listField("addresses", r -> r.addresses).maxSize(2);
        assertError(v, "addresses", "must have at most 2 items");
    }

    @Test
    public void shouldValidateListFieldEachElement() {
        List<Address> list = Arrays.asList(new Address(null, "12345"));
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null, list));
        v.listField("addresses", r -> r.addresses)
                .each(a -> {
                    a.field("city", x -> x.city).required();
                    a.field("zip", x -> x.zip).required().matches("[0-9]{5}");
                });
        assertError(v, "addresses[0].city", "must not be blank");
        assertNoErrors_forField(v, "addresses[0].zip");
    }

    @Test
    public void shouldSkipListFieldEachValidationWhenNull() {
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null));
        v.listField("addresses", r -> r.addresses)
                .each(a -> a.field("city", x -> x.city).required());
        assertNoErrors(v);
    }

    @Test
    public void shouldPassListFieldEachValidationWhenAllValid() {
        List<Address> list = Arrays.asList(new Address("NYC", "12345"), new Address("LA", "90001"));
        Validator<Body, ErrorMessageResponse> v = validator(new Body(null, null, null, null, list));
        v.listField("addresses", r -> r.addresses)
                .each(a -> {
                    a.field("city", x -> x.city).required();
                    a.field("zip", x -> x.zip).required().matches("[0-9]{5}");
                });
        assertNoErrors(v);
    }

    // --- helpers ---

    private void assertError(Validator<?, ?> v, String field, String message) {
        List<String> errs = v.errors.get(field);
        assertNotNull("Expected error for field: " + field, errs);
        assertTrue("Expected message '" + message + "' in " + errs, errs.contains(message));
    }

    private void assertNoErrors(Validator<?, ?> v) {
        assertTrue("Expected no errors but got: " + v.errors, v.errors.isEmpty());
    }

    private void assertNoErrors_forField(Validator<?, ?> v, String field) {
        assertFalse("Expected no error for field: " + field, v.errors.containsKey(field));
    }
}
