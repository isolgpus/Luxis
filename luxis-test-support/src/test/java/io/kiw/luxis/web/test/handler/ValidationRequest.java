package io.kiw.luxis.web.test.handler;

public class ValidationRequest {
    public String name;
    public String email;
    public Integer age;
    public AddressBody address;

    public static class AddressBody {
        public String city;
        public String zip;
    }
}
