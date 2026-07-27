package com.portalphotos.app.data.metadata

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FormattedMetadata(
    val formattedDate: String,
    val relativeAge: String,
    val locationName: String? = null,
    val cameraModel: String? = null,
    val cameraSettings: String? = null,
    val resolutionString: String? = null,
    val albumTitle: String = ""
)

object MediaMetadataExtractor {

    private const val TAG = "MediaMetadataExtractor"

    /**
     * Extracts rich metadata from an image file cached on disk, accounting for the photo's local timezone.
     */
    fun extractMetadata(
        context: Context,
        imageFile: File?,
        timestampMs: Long,
        albumTitle: String,
        width: Int = 0,
        height: Int = 0
    ): FormattedMetadata {
        var exif: ExifInterface? = null
        if (imageFile != null && imageFile.exists()) {
            try {
                exif = ExifInterface(imageFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read EXIF headers from ${imageFile.absolutePath}", e)
            }
        }

        // Check if EXIF contains an explicit DateTime tag (e.g. "2024:07:14 09:15:30")
        val exifTimestamp = extractTimestampFromExif(exif)
        val finalTimestamp = if (exifTimestamp != null && exifTimestamp > 0) exifTimestamp else timestampMs

        // 1. Determine TimeZone (From EXIF Offset / GPS or Fallback)
        val photoTimeZone = determineTimeZone(exif)

        // 2. Format Date: "Morning of 14 July 2024"
        val formattedDate = formatTimeOfDayDate(finalTimestamp, photoTimeZone)

        // 3. Format Relative Age: "2 years ago"
        val relativeAge = formatRelativeAge(finalTimestamp)

        // 4. Reverse Geocode GPS Location if present, otherwise fall back to Album Title with parens dates stripped
        val parsedLocation = extractLocationName(context, exif)
        val locationName = parsedLocation ?: sanitizeLocationTitle(albumTitle)

        // 5. Extract Camera Make & Model
        val cameraModel = extractCameraModel(exif)

        // 6. Extract Camera Lens & Exposure Specs
        val cameraSettings = extractCameraSettings(exif)

        // 7. Format Resolution & Megapixels
        val resString = formatResolution(exif, width, height)

        return FormattedMetadata(
            formattedDate = formattedDate,
            relativeAge = relativeAge,
            locationName = locationName,
            cameraModel = cameraModel,
            cameraSettings = cameraSettings,
            resolutionString = resString,
            albumTitle = albumTitle
        )
    }

    private fun sanitizeLocationTitle(rawTitle: String): String? {
        if (rawTitle.isBlank()) return null
        var title = rawTitle

        // Strip dates in parentheses e.g. "(July 2026)" or "(2024)"
        title = title.replace(Regex("""\s*\([A-Za-z0-9\s,-]+\)"""), "")

        val clean = title.trim()
        return clean.ifBlank { null }
    }

    private fun extractTimestampFromExif(exif: ExifInterface?): Long? {
        if (exif == null) return null
        val dateTimeStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        if (!dateTimeStr.isNullOrBlank()) {
            try {
                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                val date = sdf.parse(dateTimeStr)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        return null
    }

    private fun determineTimeZone(exif: ExifInterface?): TimeZone {
        if (exif != null) {
            // Check EXIF Offset Time Original (e.g., "+09:00" for Korea)
            val offsetStr = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
            if (!offsetStr.isNullOrBlank()) {
                val customTz = TimeZone.getTimeZone("GMT$offsetStr")
                if (customTz != null) return customTz
            }

            // Check GPS Coordinates to infer TimeZone
            val latLong = exif.latLong
            if (latLong != null && latLong.size >= 2) {
                val tzId = getTzIdFromCoords(latLong[0], latLong[1])
                if (tzId != null) {
                    return TimeZone.getTimeZone(tzId)
                }
            }
        }
        return TimeZone.getDefault()
    }

    private fun getTzIdFromCoords(lat: Double, lng: Double): String? {
        val hoursOffset = (lng / 15.0).toInt()
        return if (hoursOffset >= 0) "GMT+$hoursOffset" else "GMT$hoursOffset"
    }

    private fun formatTimeOfDayDate(timestampMs: Long, timeZone: TimeZone): String {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = if (timestampMs > 0) timestampMs else System.currentTimeMillis()
        }

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }

        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val monthYear = monthFormat.format(Date(cal.timeInMillis))

        return "$timeOfDay of $day $monthYear"
    }

    private fun formatRelativeAge(timestampMs: Long): String {
        if (timestampMs <= 0) return "Recent"
        val now = System.currentTimeMillis()
        val diffMs = now - timestampMs

        val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
        val diffYears = (diffDays / 365.25).toInt()

        return when {
            diffDays < 1 -> "Today"
            diffDays == 1 -> "Yesterday"
            diffDays < 30 -> "$diffDays days ago"
            diffDays < 365 -> "${diffDays / 30} months ago"
            diffYears == 1 -> "1 year ago"
            diffYears > 1 -> "$diffYears years ago"
            else -> "Recent"
        }
    }

    private fun extractLocationName(context: Context, exif: ExifInterface?): String? {
        val latLong = exif?.latLong ?: return null
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "${String.format(Locale.US, "%.2f", latLong[0])}°, ${String.format(Locale.US, "%.2f", latLong[1])}°"
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val country = addr.countryName
                    if (city != null && country != null) "$city, $country" else city ?: country
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCameraModel(exif: ExifInterface?): String? {
        if (exif == null) return null
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
        var model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()

        if (model.isNullOrBlank()) return make

        if (make != null && model.startsWith(make, ignoreCase = true)) {
            return model
        }
        return if (make != null) "$make $model" else model
    }

    private fun extractCameraSettings(exif: ExifInterface?): String? {
        if (exif == null) return null
        val parts = mutableListOf<String>()

        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        if (focal > 0) parts.add("${focal.toInt()}mm")

        val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        if (aperture > 0) parts.add(String.format(Locale.US, "f/%.1f", aperture))

        val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        if (exposureTime > 0) {
            if (exposureTime < 1.0) {
                parts.add("1/${(1.0 / exposureTime).toInt()}s")
            } else {
                parts.add("${exposureTime}s")
            }
        }

        @Suppress("DEPRECATION")
        val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
        if (!iso.isNullOrBlank()) parts.add("ISO $iso")

        return if (parts.isNotEmpty()) parts.joinToString(" • ") else null
    }

    private fun formatResolution(exif: ExifInterface?, fallbackW: Int, fallbackH: Int): String? {
        var w = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0) ?: 0
        var h = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0) ?: 0

        if (w == 0 || h == 0) {
            w = fallbackW
            h = fallbackH
        }

        if (w <= 0 || h <= 0) return null

        val megaPixels = (w.toLong() * h.toLong()) / 1_000_000.0
        return String.format(Locale.US, "%d × %d (%.1f MP)", w, h, megaPixels)
    }
}
