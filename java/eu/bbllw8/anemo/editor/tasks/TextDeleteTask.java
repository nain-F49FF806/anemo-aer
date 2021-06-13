/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eu.bbllw8.anemo.editor.tasks;

import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

public final class TextDeleteTask implements Callable<String> {
    @NonNull
    private final String toDelete;
    @NonNull
    private final String content;

    public TextDeleteTask(@NonNull String toDelete,
                          @NonNull String content) {
        this.toDelete = toDelete;
        this.content = content;
    }

    @Override
    public String call() {
        return new TextSubstituteAllTask(toDelete, "", content).call();
    }
}