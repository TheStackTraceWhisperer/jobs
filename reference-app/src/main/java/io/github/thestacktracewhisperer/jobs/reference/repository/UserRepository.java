package io.github.thestacktracewhisperer.jobs.reference.repository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Mock user repository for demonstration.
 */
@Repository
public class UserRepository {

    /**
     * Streams active user IDs to avoid loading all into memory.
     * 
     * @return stream of user IDs
     */
    public Stream<UUID> streamActiveUserIds() {
        // In a real implementation, this would stream from a database
        return List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        ).stream();
    }
}
