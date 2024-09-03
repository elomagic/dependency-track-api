/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.tasks;

import alpine.common.logging.Logger;
import alpine.event.framework.Event;
import alpine.event.framework.Subscriber;
import alpine.model.ConfigProperty;
import alpine.model.Team;
import alpine.persistence.PaginatedResult;

import org.apache.commons.lang3.StringUtils;
import org.dependencytrack.event.CleanupEvent;
import org.dependencytrack.model.Project;
import org.dependencytrack.persistence.QueryManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.dependencytrack.model.ConfigPropertyConstants.CLEANUP_DELETE_PROJECT;
import static org.dependencytrack.model.ConfigPropertyConstants.CLEANUP_ENABLED;
import static org.dependencytrack.model.ConfigPropertyConstants.CLEANUP_OLDER_THEN_DAYS;
import static org.dependencytrack.model.ConfigPropertyConstants.CLEANUP_VERSION_MATCH;

public class CleanupTask implements Subscriber {

    private static final Logger LOGGER = Logger.getLogger(CleanupTask.class);

    @Override
    public void inform(Event e) {
        if (e instanceof CleanupEvent) {
            try (QueryManager qm = new QueryManager()) {
                final ConfigProperty enabledProperty = qm.getConfigProperty(
                        CLEANUP_ENABLED.getGroupName(),
                        CLEANUP_ENABLED.getPropertyName()
                );
                final ConfigProperty versionMatchProperty = qm.getConfigProperty(
                        CLEANUP_VERSION_MATCH.getGroupName(),
                        CLEANUP_VERSION_MATCH.getPropertyName()
                );
                final ConfigProperty olderThenDaysProperty = qm.getConfigProperty(
                        CLEANUP_OLDER_THEN_DAYS.getGroupName(),
                        CLEANUP_OLDER_THEN_DAYS.getPropertyName()
                );
                final ConfigProperty deleteProjectProperty = qm.getConfigProperty(
                        CLEANUP_DELETE_PROJECT.getGroupName(),
                        CLEANUP_DELETE_PROJECT.getPropertyName()
                );

                if (!Boolean.TRUE.toString().equalsIgnoreCase(enabledProperty.getPropertyValue())) {
                    LOGGER.info("Cleaning up task is disabled");
                    return;
                }

                String regExMatch = Optional.ofNullable(versionMatchProperty)
                        .map(ConfigProperty::getPropertyValue)
                        .map(StringUtils::trimToNull)
                        .orElseThrow(() -> new IllegalStateException("No version regex configured"));

                int olderThenDays = Optional.ofNullable(olderThenDaysProperty)
                        .map(ConfigProperty::getPropertyValue)
                        .map(Integer::parseInt)
                        .orElseThrow(() -> new IllegalStateException("No older then days configured"));

                LOGGER.info("Running cleaning task");

                Team notAssignedToTeam = null;

                final PaginatedResult result = qm.getProjects(false, true, false, notAssignedToTeam);

                Date notBefore = Timestamp.valueOf(LocalDateTime.now().minusDays(olderThenDays));

                List<Project> list = result.getList(Project.class)
                        .stream()
                        // Filter date
                        .filter(p -> p.getLastBomImport() != null && notBefore.after(p.getLastBomImport()))
                        // Filter version
                        .filter(p -> p.getVersion().matches(regExMatch))
                        .toList();

                LOGGER.info("Cleaning up " + list.size() + " projects");

                if (Boolean.TRUE.equals(Boolean.valueOf(deleteProjectProperty.getPropertyValue()))) {
                    list.forEach(p -> qm.recursivelyDelete(p, true));
                } else {
                    list.forEach(p -> {
                        p.setActive(false);
                        qm.updateProject(p, true);
                    });
                }
            }
        }
    }

}
