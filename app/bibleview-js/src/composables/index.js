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
 */

import {getCurrentInstance, inject, onMounted, onUnmounted, reactive, ref, watch} from "@vue/runtime-core";
import {sprintf} from "sprintf-js";
import {stubsFor} from "@/utils";
import {computed} from "@vue/reactivity";
import {throttle} from "lodash";
import {Deferred} from "@/code/utils";
import {Events, emit, setupEventBusListener} from "@/eventbus";

let developmentMode = false;

if(process.env.NODE_ENV === "development") {
    developmentMode = true;
}

export function useVerseNotifier(config, android, topElement) {
    const currentVerse = ref(null);
    watch(() => currentVerse.value,  value => android.scrolledToVerse(value));

    const lineHeight = computed(() => parseFloat(window.getComputedStyle(topElement.value).getPropertyValue('line-height')));

    const onScroll = throttle(() => {
        const y = config.toolbarOffset + lineHeight.value*0.8;

        // Find element, starting from right
        const step = 10;
        let element;
        for(let x = window.innerWidth - step; x > 0; x-=step) {
            element = document.elementFromPoint(x, y)
            if(element) {
                element = element.closest(".verse");
                if(element) {
                    currentVerse.value = parseInt(element.id.slice(2))
                    break;
                }
            }
        }
    }, 50);

    onMounted(() => window.addEventListener('scroll', onScroll));
    onUnmounted(() => window.removeEventListener('scroll', onScroll));
    return {currentVerse}
}

export function useConfig() {
    const config = reactive({
        chapterNumbers: true,
        showVerseNumbers: true,
        showStrongs: false,
        showMorphology: false,
        showRedLetters: false,
        showVersePerLine: false,
        showNonCanonical: true,
        makeNonCanonicalItalic: true,
        showSectionTitles: true,
        showStrongsSeparately: false,
        showCrossReferences: true,
        showFootNotes: true,

        font: {
            fontFamily: "sans-serif",
            fontSize: 16,
        },
        showBookmarks: false,
        showMyNotes: false,

        colors: {
            dayBackground: null,
            dayNoise: 0,
            dayTextColor: null,
            nightBackground: null,
            nightNoise: 0,
            nightTextColor: null,
        },

        maxWidth: 170,
        textColor: "black",
        hyphenation: true,
        noiseOpacity: 50,
        lineSpacing: 16,
        justifyText: false,
        marginSize: {
            marginLeft: 0,
            marginRight: 0,
            marginWidth: 170,
        },

        toolbarOffset: 100,
        infiniteScroll: true,

        developmentMode: developmentMode,
    })

    setupEventBusListener(Events.SET_CONFIG, (c) => {
        for (const i in c) {
            if (config[i] !== undefined) {
                config[i] = c[i];
            } else {
                console.error("Unknown setting", i, c[i]);
            }
        }
    })

    return {config};
}

export function useStrings() {
    return {
        chapterNum: "Chapter %d. ",
        verseNum: "%d ",
        noteText: "Notes",
        crossReferenceText: "Crossreferences",
    }
}

export function useCommon() {
    const currentInstance = getCurrentInstance();

    const config = inject("config");
    const strings = inject("strings");
    //const verseInfo = inject("verseInfo", getVerseInfo(currentInstance.props));
    //const {elementCount} = inject("fragmentInfo");
    const contentTag = ref(null);
    //const thisCount = ref(-1);

    const unusedAttrs = Object.keys(currentInstance.attrs).filter(v => !v.startsWith("__") && v !== "onClose");
    if(unusedAttrs.length > 0) {
        console.error("Unhandled attributes", currentInstance.type.name, currentInstance.attrs);
    }

    onMounted(() => {
        if(!currentInstance.type.noContentTag && contentTag.value === null) {
            console.error(`${currentInstance.type.name}: contentTag does not exist`);
        }

        //thisCount.value = elementCount.value;
        //elementCount.value ++;
        //if(contentTag.value) {
        //    contentTag.value.classList.add("osis");
        //    contentTag.value.dataset.elementCount = thisCount.value.toString();
        //    contentTag.value.dataset.ordinal = verseInfo ? JSON.stringify(verseInfo.ordinal) : null;
        //    contentTag.value.dataset.osisID = verseInfo ? JSON.stringify(verseInfo.osisID) : null;
        //}
    });

    function split(string, separator, n) {
        return string.split(separator)[n]
    }

    return {config, strings, contentTag, sprintf, split}
}
