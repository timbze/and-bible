/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

/**
 *
 */
package net.bible.service.db.readingplan

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.BaseColumns
import android.provider.OpenableColumns
import android.util.Log
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.service.common.CommonUtils
import net.bible.service.readingplan.PassageReader

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.trim
import org.crosswire.common.util.IOUtil
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemKJV
import org.crosswire.jsword.versification.system.SystemNRSVA
import org.crosswire.jsword.versification.system.Versifications
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.IOException
import java.util.Properties
import kotlin.collections.ArrayList

/** @author Timmy Braun [tim.bze at gmail dot com] (2/12/2019)
 */
object ReadingPlanDatabaseDefinition {

    object ReadingPlanMeta : BaseColumns {
        // If COLUMN_CURRENT_DAY has CONSTANT_CURRENT_DAY_BY_DATE value,
        // then the reading plan will by default get today's date
        const val CONSTANT_CURRENT_DAY_BY_DATE = -1

        const val TABLE_NAME = "readingplan_meta"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_PLAN_FILE_NAME = "plan_file_name"
        const val COLUMN_PLAN_NAME = "plan_name"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_DATE_START = "date_start"
        const val COLUMN_DAYS_IN_PLAN = "days_in_plan"
        const val COLUMN_CURRENT_DAY = "current_day"
        const val COLUMN_VERSIFICATION_NAME = "versification"
    }

    object ReadingPlanDays : BaseColumns {
        const val TABLE_NAME = "readingplan_days"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_READING_PLAN_META_ID = "readingplan_meta_id"
        const val COLUMN_DAY_NUMBER = "day_number"
        const val COLUMN_READING_DATE = "reading_date"
        const val COLUMN_DAY_CHAPTERS = "day_chapters"
        const val COLUMN_READ_STATUS = "read_status"
    }

    class Operations {
        companion object {
            private const val TAG = "ReadingPlanDatabaseOps"
            private const val SQL_CREATE_ENTRIES_META =
                """CREATE TABLE ${ReadingPlanMeta.TABLE_NAME} (
                ${ReadingPlanMeta.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ReadingPlanMeta.COLUMN_PLAN_FILE_NAME} TEXT NOT NULL,
                ${ReadingPlanMeta.COLUMN_PLAN_NAME} TEXT NOT NULL,
                ${ReadingPlanMeta.COLUMN_DESCRIPTION} TEXT NOT NULL,
                ${ReadingPlanMeta.COLUMN_DATE_START} INTEGER,
                ${ReadingPlanMeta.COLUMN_DAYS_IN_PLAN} INTEGER,
                ${ReadingPlanMeta.COLUMN_CURRENT_DAY} INTEGER NOT NULL DEFAULT 0,
                ${ReadingPlanMeta.COLUMN_VERSIFICATION_NAME} TEXT NOT NULL
                ); """

            private const val SQL_CREATE_ENTRIES_DAYS =
                """CREATE TABLE ${ReadingPlanDays.TABLE_NAME} (
                ${ReadingPlanDays.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ReadingPlanDays.COLUMN_READING_PLAN_META_ID} INTEGER NOT NULL,
                ${ReadingPlanDays.COLUMN_DAY_NUMBER} INTEGER NOT NULL,
                ${ReadingPlanDays.COLUMN_READING_DATE} TEXT,
                ${ReadingPlanDays.COLUMN_DAY_CHAPTERS} TEXT NOT NULL,
                ${ReadingPlanDays.COLUMN_READ_STATUS} TEXT
                );"""

            private const val SQL_CREATE_DAYS_INDEX =
                "CREATE INDEX days_date_name ON ${ReadingPlanDays.TABLE_NAME}(${ReadingPlanDays.COLUMN_READING_DATE});"

            private const val VERSIFICATION = "Versification"
            private const val DOT_PROPERTIES = ".properties"
            private const val READING_PLAN_FOLDER = SharedConstants.READINGPLAN_DIR_NAME
            private val USER_READING_PLAN_FOLDER = SharedConstants.MANUAL_READINGPLAN_DIR


            /** Called from CommonDatabaseHelper when ReadingPlan tables
             * don't exists and the helper class needs to create them.
             */
            @JvmStatic
            fun onCreate(db: SQLiteDatabase) {
                Log.i(TAG, "Creating AndBible tables (ReadingPlan)")
                db.execSQL(SQL_CREATE_ENTRIES_META)
                db.execSQL(SQL_CREATE_ENTRIES_DAYS)

                importReadingPlansToDatabase(db)

                db.execSQL(SQL_CREATE_DAYS_INDEX)
            }

            fun importReadingPlansToDatabase(db: SQLiteDatabase, uri: Uri? = null): Boolean {
                // dbAdapter can ONLY be used in this function if it's not [firstImport]
                lateinit var dbAdapter: ReadingPlanDBAdapter
                lateinit var readingPlanProperties: ArrayList<ReadingPlanPropertiesFromText>
                lateinit var planCode: String
                var customInput: InputStream? = null
                val firstImport = uri == null
                if (!firstImport) {
                    dbAdapter = ReadingPlanDBAdapter()
                    customInput = BibleApplication.application.contentResolver.openInputStream(uri!!)
                    planCode = getPlanCodeAsFileName(uri)
                }
                var returnValue = false

                readingPlanProperties = when (firstImport) {
                    true -> getAllReadingPlanProperties
                    else -> ArrayList(arrayListOf(loadProperties(customInput!!, planCode)))
                }

                for (property in readingPlanProperties) {
                    Log.d(TAG, "Parsing ${property.planCode}, ${property.planName} plan")
                    if (firstImport ||
                        (!firstImport &&
                            !dbAdapter.getIsPlanAlreadyImported(property.planCode)
                            )
                    ) {

                        val dbReadingPlanMetaID = insertDatabaseMetaRecord(property, firstImport, db)
                        val planReadingList = getDailyReadingsFromProperties(property)

                        for (dailyReading in planReadingList) {
                            Log.d(TAG, "Parsing day ${dailyReading.dayNumber}, ${property.planName} plan")
                            val isDateBasedPlan = dailyReading.dateBasedDay != null

                            // Update meta table to specify plan as Date-Based
                            if (isDateBasedPlan) {
                                val values = ContentValues().apply {
                                    put(ReadingPlanMeta.COLUMN_CURRENT_DAY, ReadingPlanMeta.CONSTANT_CURRENT_DAY_BY_DATE)
                                }
                                val whereClause = "${ReadingPlanMeta.COLUMN_ID} = ?"
                                val whereArgs = arrayOf(dbReadingPlanMetaID.toString())
                                db.update(ReadingPlanMeta.TABLE_NAME, values, whereClause, whereArgs)
                            }

                            val readStatusString: String? = getReadStatusString(dailyReading, property, dbReadingPlanMetaID)

                            // Enter day info to DB
                            val dayValues = ContentValues().apply {
                                put(ReadingPlanDays.COLUMN_READING_PLAN_META_ID, dbReadingPlanMetaID)
                                put(ReadingPlanDays.COLUMN_DAY_NUMBER, dailyReading.dayNumber)
                                put(ReadingPlanDays.COLUMN_DAY_CHAPTERS, dailyReading.dailyReadings)
                                put(ReadingPlanDays.COLUMN_READ_STATUS, readStatusString)
                                if (isDateBasedPlan) {
                                    put(
                                        ReadingPlanDays.COLUMN_READING_DATE,
                                        dailyReading.dateBasedDay
                                    )
                                }
                            }
                            db.insert(ReadingPlanDays.TABLE_NAME, null, dayValues)
                        }
                        returnValue = true
                    }
                }
                return returnValue
            }

            private fun getPlanCodeAsFileName(uri: Uri): String {
                val c = BibleApplication.application.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                if (c != null && c.moveToFirst()) {
                    val code = c.getString(0)
                    c.close()
                    Log.d(TAG, "File name as plan code retrieved from file system is $code")
                    return code.replace(".properties", "")
                }
                return ""
            }

            private fun insertDatabaseMetaRecord(
                property: ReadingPlanPropertiesFromText,
                firstImport: Boolean,
                db: SQLiteDatabase
            ): Long {
                val prefs = CommonUtils.getSharedPreferences()
                val currentPlanCode = prefs.getString("reading_plan", "") as String
                val thisPlanDay: Int = prefs.getInt(property.planCode + "_day", 1)
                val startDate = CommonUtils.getSharedPreferences().getLong(property.planCode + "_start", 0)

                val metaValues = ContentValues().apply {
                    put(ReadingPlanMeta.COLUMN_PLAN_FILE_NAME, property.planCode)
                    put(ReadingPlanMeta.COLUMN_PLAN_NAME, property.planName)
                    put(ReadingPlanMeta.COLUMN_DESCRIPTION, property.planDescription)
                    put(ReadingPlanMeta.COLUMN_DAYS_IN_PLAN, property.numberOfPlanDays)
                    put(ReadingPlanMeta.COLUMN_VERSIFICATION_NAME, property.versification?.name)
                    put(ReadingPlanMeta.COLUMN_CURRENT_DAY, thisPlanDay)
                    if (startDate > 0L) put(ReadingPlanMeta.COLUMN_DATE_START, startDate)
                }

                val dbReadingPlanMetaID = db.insert(ReadingPlanMeta.TABLE_NAME, null, metaValues)
                if (firstImport && currentPlanCode == property.planCode) {
                    prefs.edit().putInt(ReadingPlanDBAdapter.CURRENT_PLAN_INDEX,dbReadingPlanMetaID.toInt()).apply()
                }

                return dbReadingPlanMetaID
            }

            private fun getReadStatusString(
                dailyReading: DayInformationForImport,
                property: ReadingPlanPropertiesFromText,
                dbReadingPlanMetaID: Long
            ): String? {
                val prefs = CommonUtils.getSharedPreferences()
                val thisPlanDay: Int = prefs.getInt(property.planCode + "_day", 1)
                val chaptersReadArray = ArrayList<ReadingPlanOneDayDB.ChapterRead?>()
                if (dailyReading.dayNumber == thisPlanDay) {
                    val prefsKey: String = property.planCode + "_" + dailyReading.dayNumber
                    val prefsStatusString = prefs.getString(prefsKey, null)

                    if (prefsStatusString != null) {
                        for (i in 0 until dailyReading.numberOfReadings) {
                            if (i < prefsStatusString.length && prefsStatusString[i] == '1') {
                                chaptersReadArray.add(
                                    ReadingPlanOneDayDB.ChapterRead(i + 1, true)
                                )
                            } else {
                                chaptersReadArray.add(
                                    ReadingPlanOneDayDB.ChapterRead(i + 1, false)
                                )
                            }
                        }
                        return ReadingPlanOneDayDB.ReadingStatus(
                            dbReadingPlanMetaID.toInt(),
                            dailyReading.numberOfReadings,
                            chaptersReadArray
                        ).toJsonString()
                    }
                } else if (dailyReading.dayNumber < thisPlanDay) {
                    for (i in 0 until dailyReading.numberOfReadings) {
                        chaptersReadArray[i] =
                            ReadingPlanOneDayDB.ChapterRead(i + 1, true)
                    }
                    return ReadingPlanOneDayDB.ReadingStatus(
                        dbReadingPlanMetaID.toInt(),
                        dailyReading.numberOfReadings,
                        chaptersReadArray
                    ).toJsonString()
                }
                return null
            }

            private val getAllReadingPlanProperties: ArrayList<ReadingPlanPropertiesFromText>
                @Throws(IOException::class)
                get() {

                    val resources = BibleApplication.application.resources
                    val assetManager = resources.assets

                    val allPlanCodes = ArrayList<ReadingPlanPropertiesFromText>()

                    val internalPlans = assetManager.list(READING_PLAN_FOLDER)
                    if(internalPlans != null) {
                        allPlanCodes.addAll(
                            getReadingPlanProperties(
                                getPropertiesOnlyFromFiles(internalPlans),
                                false
                            )
                        )
                    }

                    val userPlans = USER_READING_PLAN_FOLDER.list()
                    if (userPlans != null) {
                        allPlanCodes.addAll(
                            getReadingPlanProperties(
                                getPropertiesOnlyFromFiles(userPlans),
                                true
                            )
                        )
                    }

                    return allPlanCodes
                }

            private fun getPropertiesOnlyFromFiles(files: Array<String>): ArrayList<String> {
                Log.d(TAG, "Get reading plan codes = $files")


                val codes = ArrayList<String>()
                for (file in files) {
                    if (file.endsWith(DOT_PROPERTIES)) {
                        codes.add(file.replace(DOT_PROPERTIES, ""))
                    }
                }
                return codes
            }

            private fun getReadingPlanProperties(
                planCodes: ArrayList<String>,
                isUserPlan: Boolean,
                inputStream_: InputStream? = null
            ): ArrayList<ReadingPlanPropertiesFromText>
            {
                val resources = BibleApplication.application.resources
                val assetManager = resources.assets
                val isImportPlan = inputStream_ != null
                if (isImportPlan) planCodes.add("")

                val propertiesArrayReturn = ArrayList<ReadingPlanPropertiesFromText>()
                lateinit var inputStream: InputStream
                // bufferedReader is for comment lines in start of file for Plan Name
                // because we can not read inputStream twice.
                lateinit var bufferedReader: BufferedReader
                try {

                    for (code in planCodes) {
                        val filename = code + DOT_PROPERTIES
                        val userReadingPlanFile = File(USER_READING_PLAN_FOLDER, filename)

                        inputStream = when (true) {
                            isImportPlan -> inputStream_!!
                            isUserPlan -> FileInputStream(userReadingPlanFile)
                            else -> assetManager.open(READING_PLAN_FOLDER + File.separator + filename)
                        }

                        val properties = loadProperties(inputStream, code)

                        propertiesArrayReturn.add(properties)
                    }

                    Log.d(TAG, "All properties are now loaded for import")
                } catch (e: IOException) {
                    System.err.println("Failed to open reading plan property file")
                    e.printStackTrace()
                } finally {
                    IOUtil.close(inputStream)
                }
                return propertiesArrayReturn
            }

            private fun loadProperties(inputStream_: InputStream, planCode: String = ""): ReadingPlanPropertiesFromText {
                val properties = ReadingPlanPropertiesFromText()
                val byteArrayForReuse = ByteArrayOutputStream()
                byteArrayForReuse.write(inputStream_.readBytes())
                inputStream_.close()
                val inputStream1 = ByteArrayInputStream(byteArrayForReuse.toByteArray())
                val inputStream2 = ByteArrayInputStream(byteArrayForReuse.toByteArray())
                properties.load(inputStream1)

                var lineCount = 0
                // Get first commented lines from file for Plan Name (first line)
                // and Description (following commented lines)
                inputStream2.bufferedReader().forEachLine {
                    if (it.startsWith("#")) {
                        val lineWithoutCommentMarks: String = it.trim().replaceFirst("^(\\s*#*\\s*)".toRegex(), "")
                        Log.d(TAG, lineWithoutCommentMarks)
                        if (lineCount == 0) {
                            properties.planName = trim(lineWithoutCommentMarks)
                        } else {
                            properties.planDescription = trim(properties.planDescription + lineWithoutCommentMarks + "\n")
                        }
                        lineCount++
                    }
                }
                properties.planCode = planCode
                properties.numberOfPlanDays = getNumberOfPlanDays(properties)
                properties.versification = try {
                    Versifications.instance().getVersification(properties.getProperty(VERSIFICATION, SystemKJV.V11N_NAME))
                } catch (e: Exception) {
                    // This will probably never be called, but maybe? This is how it was from before.
                    Versifications.instance().getVersification(SystemNRSVA.V11N_NAME)
                }
                Log.e(TAG, "planDays=${properties.numberOfPlanDays}")
                return properties
            }

            /** Get last day number - there may be missed days so cannot simply do props.size()
             */
            private fun getNumberOfPlanDays(planProperties: ReadingPlanPropertiesFromText): Int {
                var maxDayNumber = 0

                for (line in planProperties.keys) {
                    val propertyName = line as String
                    if (StringUtils.isNumeric(propertyName)) {
                        val dayNo = Integer.parseInt(propertyName)
                        maxDayNumber = Math.max(maxDayNumber, dayNo)
                    } else {
                        if (!VERSIFICATION.equals(propertyName, ignoreCase = true)) {
                            Log.e(TAG, "Invalid day number:$propertyName")
                        }
                    }
                }

                return maxDayNumber
            }

            /** get a list of all days readings in a plan
             */
            private fun getDailyReadingsFromProperties(
                properties: ReadingPlanPropertiesFromText
            ): ArrayList<DayInformationForImport>
            {
                val returnArray = ArrayList<DayInformationForImport>()
                for ((key1, value1) in properties) {
                    val keyString = key1 as String
                    val readingsStringOrig = value1 as String

                    if (StringUtils.isNumeric(keyString)) {
                        val dayNumber = Integer.parseInt(keyString)
                        var dateBasedDay: String? = null
                        var readingsString: String = readingsStringOrig

                        // Separate Date and Readings from date-based plans
                        if (StringUtils.isNotEmpty(readingsStringOrig) && StringUtils.contains(readingsStringOrig,";")) {
                            // Check if string contains : (Would happen in case of date-based plan, shows Feb-1:Gen.1,Exo.1)
                                dateBasedDay = readingsStringOrig.replace(";.*".toRegex(),"") // like Feb-1
                                readingsString = readingsStringOrig.replace("^.*;".toRegex(),"")
                        } else if (StringUtils.isEmpty(readingsStringOrig)) {
                            Log.e(TAG, "Day $dayNumber has no readings.")
                        }

                        // Check reading keys to see that they are valid Bible references
                        val passageReader = PassageReader(properties.versification!!)
                        val readingArray =
                            readingsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val readingsCount = readingArray.count()
                        for (reading in readingArray) {
                            val emptyPassageKey = PassageKeyFactory.instance().createEmptyKeyList(properties.versification!!)
                            if (passageReader.getKey(reading) == emptyPassageKey)
                                Log.e(TAG, "Invalid passage $reading on day $dayNumber")
                        }

                        val daysReading =
                            DayInformationForImport(
                                dayNumber,
                                dateBasedDay,
                                readingsString,
                                readingsCount
                            )
                        returnArray.add(daysReading)
                    }
                }

                return returnArray
            }

            private data class DayInformationForImport(
                val dayNumber: Int,
                val dateBasedDay: String?,
                val dailyReadings: String,
                val numberOfReadings: Int
            )

            private class ReadingPlanPropertiesFromText: Properties() {
                var planCode: String = ""
                var planName: String = ""
                var planDescription: String = ""
                var versification: Versification? = null
                var numberOfPlanDays: Int = 0
            }

        }

    }
}