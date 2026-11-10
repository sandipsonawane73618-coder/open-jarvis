package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeStepIntentParserTest {
    @Test fun parsesValidAlarmAndTimer() {
        assertEquals(StepIntent.Alarm(7, 30), NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":7,"minute":30}}"""))
        assertEquals(StepIntent.Timer(90), NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":90}}"""))
    }

    @Test fun parsesBooleanAndTextIntents() {
        assertEquals(StepIntent.Torch(true), NativeStepIntentParser.parse("""{"skill":"torch","args":{"on":true}}"""))
        assertEquals(StepIntent.Volume(false), NativeStepIntentParser.parse("""{"skill":"volume","args":{"up":false}}"""))
        assertEquals(StepIntent.Navigate("Pune"), NativeStepIntentParser.parse("""{"skill":"navigate","args":{"place":"Pune"}}"""))
        assertEquals(StepIntent.AppLaunch("Chrome"), NativeStepIntentParser.parse("""{"skill":"app_launch","args":{"label":"Chrome"}}"""))
    }

    @Test fun rejectsInvalidJsonAndOutOfRangeArguments() {
        assertNull(NativeStepIntentParser.parse("not json"))
        assertNull(NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":24,"minute":0}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":0}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"navigate","args":{"place":""}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"torch","args":{"on":"yes"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":"90"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"timer","args":{"seconds":90.5}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"alarm","args":{"hour":7.5,"minute":30}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{"contact":42,"text":"hello"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"app_launch","args":{"label":"Chrome","extra":"x"}}"""))
    }

    @Test fun noneAndUnknownSkillsReturnNull() {
        assertNull(NativeStepIntentParser.parse("""{"skill":"none","args":{}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"unknown","args":{}}"""))
    }

    @Test fun parsesMessageIntentWithoutSending() {
        assertEquals(
            StepIntent.Message("whatsapp", "Amit", "I am on the way"),
            NativeStepIntentParser.parse("""{"skill":"message","args":{"app":"whatsapp","contact":"Amit","text":"I am on the way"}}""")
        )
        assertEquals(
            StepIntent.Message("sms", null, "Reached"),
            NativeStepIntentParser.parse("""{"skill":"message","args":{"app":"sms","text":"Reached"}}""")
        )
    }

    @Test fun rejectsUnsafeOrAmbiguousMessageAndEnvelopeData() {
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{"app":"telegram","contact":"Amit","text":"Hi"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{"app":"whatsapp","contact":42,"text":"Hi"}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"message","args":{"contact":"Amit","text":"Hi","send":true}}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"torch","args":{"on":true},"extra":1}"""))
        assertNull(NativeStepIntentParser.parse("""{"skill":"none","args":{"ignored":true}}"""))
    }

}
