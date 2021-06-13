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

package net.bible.android.database.readingplan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

class ReadingPlanEntities {

    @Entity
    data class ReadingPlan(
        /** Previously called planCode */
        @PrimaryKey
        val fileName: String,
        var startDate: Date? = null,
        var dayComplete: Int? = null,
        /** This is so that reading plan status can store historical reading records and not only the
         * current reading cycle. This is not a count of how many times the user has been through the plan,
         * it is incremented every time the plan is reset */
        var readIteration: Int? = null,
        /**
         * For those reading plans that were in progress during db v51-52 upgrade. ReadingPlanHistory will now save
         * records for each read day but status table in v51 was only used while day reading was in progress, then deleted
         * when day is done. With "upgraded" field we know that if minimum day in ReadingPlanHistory for a certain
         * plan is 45, that all days before that are done.
         */
        var upgraded: Boolean = false,
    )

    @Entity(
        primaryKeys = [ "readingPlanFileName", "dayNumber", "readIteration" ],
        foreignKeys =
        [
            ForeignKey(entity = ReadingPlan::class, parentColumns = [ "fileName" ], childColumns = [ "readingPlanFileName" ], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
        ],
        indices = [
            Index("readingPlanFileName")
        ]
    )
    data class ReadingPlanHistory(
        val readingPlanFileName: String,
        val dayNumber: Int,
        /** See note on [ReadingPlan.readIteration]*/
        var readIteration: Int,
        var dateCompleted: Date? = null,
        var readStatus: String? = null,
    )
}
