package com.chicken.client.core;

public enum Category {
    RENDER("Render"),
    MOVEMENT("Movement"),
    MISC("Misc");

    private final String title;

    Category(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
