package io.github.thestacktracewhisperer.jobs.common.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobStatusTest {

    @Test
    void testEnumValues() {
        JobStatus[] statuses = JobStatus.values();
        
        assertEquals(5, statuses.length);
        assertArrayEquals(
            new JobStatus[]{
                JobStatus.QUEUED,
                JobStatus.PROCESSING,
                JobStatus.SUCCESS,
                JobStatus.FAILED,
                JobStatus.PERMANENTLY_FAILED
            },
            statuses
        );
    }

    @Test
    void testValueOf() {
        assertEquals(JobStatus.QUEUED, JobStatus.valueOf("QUEUED"));
        assertEquals(JobStatus.PROCESSING, JobStatus.valueOf("PROCESSING"));
        assertEquals(JobStatus.SUCCESS, JobStatus.valueOf("SUCCESS"));
        assertEquals(JobStatus.FAILED, JobStatus.valueOf("FAILED"));
        assertEquals(JobStatus.PERMANENTLY_FAILED, JobStatus.valueOf("PERMANENTLY_FAILED"));
    }

    @Test
    void testValueOfThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            JobStatus.valueOf("INVALID_STATUS");
        });
    }

    @Test
    void testEnumEquality() {
        JobStatus status1 = JobStatus.QUEUED;
        JobStatus status2 = JobStatus.QUEUED;
        JobStatus status3 = JobStatus.PROCESSING;
        
        assertEquals(status1, status2);
        assertNotEquals(status1, status3);
    }

    @Test
    void testEnumOrdinal() {
        assertEquals(0, JobStatus.QUEUED.ordinal());
        assertEquals(1, JobStatus.PROCESSING.ordinal());
        assertEquals(2, JobStatus.SUCCESS.ordinal());
        assertEquals(3, JobStatus.FAILED.ordinal());
        assertEquals(4, JobStatus.PERMANENTLY_FAILED.ordinal());
    }
}
