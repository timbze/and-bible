/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.readingplan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TwoLineListItem
import net.bible.service.db.readingplan.ReadingPlanOneDayDB

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReadingItemAdapter(context: Context, private val resource: Int, items: List<ReadingPlanOneDayDB>) :
		ArrayAdapter<ReadingPlanOneDayDB>(context, resource, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val item = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view: TwoLineListItem
        if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(resource, parent, false) as TwoLineListItem
        } else {
            view = convertView as TwoLineListItem
        }

        // Set value for the first text field
        if (view.text1 != null && item != null) {
                view.text1.text =
                    if (item.readingPlanInfo.isDateBased)
                        item.dateString
                    else item.dayAndNumberDescription
        }

        // set value for the second text field
        if (view.text2 != null && item != null) {
            view.text2.text = item.readingChaptersDescription
        }

        return view
    }
}
