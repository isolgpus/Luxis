package io.kiw.luxis.web.test.handler;

public class ValidationResponse {
    public String name;
    public String email;
    public int age;
    public String city;
    public String page;
    public String userId;

    public ValidationResponse(final String name, final String email, final int age, final String city, final String page, final String userId) {
        this.name = name;
        this.email = email;
        this.age = age;
        this.city = city;
        this.page = page;
        this.userId = userId;
    }
}
