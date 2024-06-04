/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package exe.bbllw8.anemo.documents.provider

import android.database.AbstractCursor

class EmptyCursor : AbstractCursor() {
    override fun getCount(): Int {
        return 0
    }

    override fun getColumnNames(): Array<String?> {
        return arrayOfNulls(0)
    }

    override fun getString(column: Int): String? {
        return null
    }

    override fun getShort(column: Int): Short {
        return 0
    }

    override fun getInt(column: Int): Int {
        return 0
    }

    override fun getLong(column: Int): Long {
        return 0L
    }

    override fun getFloat(column: Int): Float {
        return 0f
    }

    override fun getDouble(column: Int): Double {
        return 0.0
    }

    override fun isNull(column: Int): Boolean {
        return true
    }
}
