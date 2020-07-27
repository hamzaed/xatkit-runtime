package com.xatkit.dsl.library.impl;

import com.xatkit.dsl.library.LibraryProvider;
import com.xatkit.intent.Library;
import lombok.NonNull;

public class LibraryProviderImpl implements LibraryProvider {

    protected Library library;

    public LibraryProviderImpl(@NonNull Library library) {
        this.library = library;
    }

    @Override
    public @NonNull Library getLibrary() {
        return this.library;
    }
}
