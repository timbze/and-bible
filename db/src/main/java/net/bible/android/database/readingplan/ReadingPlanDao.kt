/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanHistory

@Dao
interface ReadingPlanDao {

    //region ReadingPlanStatus
    @Query("""SELECT rh.* FROM ReadingPlanHistory rh 
            LEFT JOIN ReadingPlan r on rh.readingPlanFileName = r.fileName
            WHERE rh.readingPlanFileName = :planCode AND rh.dayNumber = :planDay AND rh.readIteration = r.readIteration""")
    suspend fun getStatus(planCode: String, planDay: Int): ReadingPlanHistory?

    // TODO don't use .REPLACE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlanStatus(status: ReadingPlanHistory)
    //endregion

    //region ReadingPlan
    @Query("SELECT * FROM ReadingPlan WHERE fileName = :planCode")
    suspend fun getPlan(planCode: String): ReadingPlan?

    // TODO don't use .REPLACE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePlan(plan: ReadingPlan)

    @Query("DELETE FROM ReadingPlan WHERE fileName = :planCode")
    suspend fun deletePlanInfo(planCode: String)

    //endregion

}
