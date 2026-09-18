package br.com.mailflow.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    boolean existsByWorkspaceIdAndEmailIgnoreCaseAndIdNot(UUID workspaceId, String email, UUID id);

    java.util.Optional<Contact> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    List<Contact> findTop100ByWorkspaceIdOrderByDisplayNameAscEmailAsc(UUID workspaceId);

    List<Contact> findByWorkspaceIdAndIdIn(UUID workspaceId, List<UUID> ids);

    @Query("""
            select c from Contact c
            where c.workspaceId = :workspaceId and (lower(c.email) like lower(concat('%', :query, '%'))
               or lower(coalesce(c.displayName, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(c.company, '')) like lower(concat('%', :query, '%')))
            order by coalesce(c.displayName, c.email), c.id
            """)
    List<Contact> search(@Param("workspaceId") UUID workspaceId, @Param("query") String query, Pageable pageable);
}
