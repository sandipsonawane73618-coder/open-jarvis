package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalFactCandidatePolicyTest {
    @Test fun detectsClearFirstPersonFactsAcrossLanguages() {
        assertEquals("I like tea", PersonalFactCandidatePolicy.extract("I like tea"))
        assertEquals("mujhe chai pasand hai", PersonalFactCandidatePolicy.extract("mujhe chai pasand hai"))
        assertEquals("मला चहा आवडते", PersonalFactCandidatePolicy.extract("मला चहा आवडते"))
        assertEquals("mera naam Sagar hai", PersonalFactCandidatePolicy.extract("mera naam Sagar hai"))
        assertEquals("I am an engineer", PersonalFactCandidatePolicy.extract("I am an engineer"))
    }

    @Test fun doesNotTreatQuestionsOrCommandsAsFacts() {
        assertNull(PersonalFactCandidatePolicy.extract("What do you remember?"))
        assertNull(PersonalFactCandidatePolicy.extract("open WhatsApp"))
        assertNull(PersonalFactCandidatePolicy.extract("Tell me a joke"))
        assertNull(PersonalFactCandidatePolicy.extract("I like tea and open YouTube"))
    }

    @Test fun requiresExplicitYesOrNoConsent() {
        assertEquals(PersonalFactCandidatePolicy.Consent.YES, PersonalFactCandidatePolicy.consent("haan"))
        assertEquals(PersonalFactCandidatePolicy.Consent.YES, PersonalFactCandidatePolicy.consent("होय"))
        assertEquals(PersonalFactCandidatePolicy.Consent.NO, PersonalFactCandidatePolicy.consent("nahi"))
        assertEquals(PersonalFactCandidatePolicy.Consent.OTHER, PersonalFactCandidatePolicy.consent("maybe"))
    }
}
