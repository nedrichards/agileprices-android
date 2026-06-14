package com.nedrichards.agileprices

import android.content.Context
import android.content.pm.PackageManager

internal fun Context.isWatchDevice(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
