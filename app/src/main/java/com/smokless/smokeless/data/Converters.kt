package com.smokless.smokeless.data

import androidx.room.TypeConverter
import com.smokless.smokeless.data.entity.Substance

class Converters {

    @TypeConverter
    fun substanceToName(substance: Substance): String = substance.name

    @TypeConverter
    fun nameToSubstance(name: String?): Substance = Substance.fromName(name)
}
