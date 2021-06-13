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

package net.bible.service.db.readingplan

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.control.ApplicationScope
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanHistory
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.readingplan.ReadingPlanInfoDto
import java.util.Date
import javax.inject.Inject
import kotlin.math.max

@ApplicationScope
class ReadingPlanRepository @Inject constructor() {
    private val readingPlanDao: ReadingPlanDao = DatabaseContainer.db.readingPlanDao()

    fun getReadingStatus(planCode: String, planDay: Int): String? = runBlocking {
        readingPlanDao.getStatus(planCode, planDay)?.readStatus }

    fun getStartDate(planCode: String) = runBlocking {
        readingPlanDao.getPlan(planCode)?.startDate
    }

    @Synchronized
    fun setReadingStatus(planCode: String, dayNo: Int, status: String) = GlobalScope.launch {
        readingPlanDao.addPlanStatus(
            ReadingPlanHistory(planCode, dayNo, status)
        )
    }

    @Synchronized
    fun startPlan(planCode: String, date: Date = CommonUtils.truncatedDate) = GlobalScope.launch {
        var readPlan = readingPlanDao.getPlan(planCode)
        readPlan = readPlan?.apply { planStartDate = date } ?: ReadingPlanOld(planCode, date)

        readingPlanDao.updatePlan(readPlan)
    }

    fun resetPlan(planCode: String) = GlobalScope.launch {
        Log.i(TAG, "Now resetting plan $planCode in database. Removing start date, current day, and read statuses")
        readingPlanDao.deleteStatusesForPlan(planCode)
        readingPlanDao.deletePlanInfo(planCode)
    }

    fun getDayComplete(planCode: String): Int = runBlocking {
        max(readingPlanDao.getPlan(planCode)?.dayComplete ?: 0,1)
    }

    @Synchronized
    fun setCurrentDay(planCode: String, dayNo: Int) = GlobalScope.launch {
        var readPlan = readingPlanDao.getPlan(planCode)
        readPlan = readPlan?.apply { planCurrentDay = dayNo } ?:
            ReadingPlanOld(planCode, CommonUtils.truncatedDate, dayNo)

        readingPlanDao.updatePlan(readPlan)
    }

    companion object {
        private val TAG = ReadingPlanRepository::class.simpleName
    }
}
