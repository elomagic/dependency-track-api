package org.dependencytrack.tasks;

import alpine.model.ConfigProperty;

import org.dependencytrack.PersistenceCapableTest;
import org.dependencytrack.event.CleanupEvent;
import org.dependencytrack.model.ConfigPropertyConstants;
import org.dependencytrack.model.Project;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dependencytrack.model.ConfigPropertyConstants.*;

public class CleanupTaskTest extends PersistenceCapableTest {

    @Before
    public void setUp() {
        setConfig(CLEANUP_ENABLED, "true");
        setConfig(CLEANUP_OLDER_THEN_DAYS, "0");
        setConfig(CLEANUP_DELETE_PROJECT, CLEANUP_DELETE_PROJECT.getDefaultPropertyValue());
        setConfig(CLEANUP_VERSION_MATCH, CLEANUP_VERSION_MATCH.getDefaultPropertyValue());
    }

    @Test
    public void testCleanupTaskInactive() {
        Project project = createProject();

        // Project must exist
        qm.getPersistenceManager().refresh(project);
        assertThat(project.getUuid()).isNotNull();

        // Perform test
        final var task = new CleanupTask();
        task.inform(new CleanupEvent());

        // Check results
        qm.getPersistenceManager().refresh(project);
        assertThat(project.isActive()).isFalse();
    }

    @Test
    public void testCleanupTaskDeleted() {
        ConfigProperty c = qm.getConfigProperty(CLEANUP_DELETE_PROJECT.getGroupName(), CLEANUP_DELETE_PROJECT.getPropertyName());
        c.setPropertyValue("true");
        qm.getPersistenceManager().makePersistent(c);

        Project project = createProject();

        // Project must exist
        qm.getPersistenceManager().refresh(project);
        assertThat(project.isActive()).isTrue();

        // Perform test
        final var task = new CleanupTask();
        task.inform(new CleanupEvent());

        // Check results
        project = qm.getProject(project.getUuid().toString());
        assertThat(project).isNull();
    }

    private void setConfig(ConfigPropertyConstants config, String value) {
        qm.createConfigProperty(
                config.getGroupName(),
                config.getPropertyName(),
                value,
                config.getPropertyType(),
                config.getDescription()
        );
    }

    private Project createProject() {
        Project project = new Project();
        project.setActive(true);
        project.setVersion("1.0.0-SNAPSHOT");
        project.setName("Project P1");
        //project.setPurl("purl:maven/groupId/artifactId/P1@1.0.0-SNAPSHOT");
        project.setLastBomImport(LocalDate.now().minusDays(10).toDate());

        return qm.createProject(project, List.of(), false);
    }

}
