package com.imjustseiyin.util;

import net.minecraft.util.StringRepresentable;

public enum LongTablePart implements StringRepresentable {
    HEAD("head"),
    FOOT("foot");

    private final String name;

    LongTablePart(String name) { this.name = name; }

    @Override public String toString() { return name; }
    @Override public String getSerializedName() { return name; }
}
