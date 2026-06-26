-- reposts

ALTER TABLE reposts DROP COLUMN first_seen;
ALter TABLE reposts ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE reposts DROP COLUMN last_seen;
ALter TABLE reposts ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE reposts
    ADD CONSTRAINT fk_reposts_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE reposts
    ADD CONSTRAINT fk_reposts_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE reposts
    DROP COLUMN last_seen_iteration;

-- achievements

ALTER TABLE achievements DROP COLUMN first_seen;
AlTer TABLE achievements ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE achievements DROP COLUMN last_seen;
AlTer TABLE achievements ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE achievements
    ADD CONSTRAINT fk_achievements_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE achievements
    ADD CONSTRAINT fk_achievements_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE achievements
    DROP COLUMN last_seen_iteration;

-- comments

ALTER TABLE comments DROP COLUMN first_seen;
AlTer TABLE comments ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE comments DROP COLUMN last_seen;
AlTer TABLE comments ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE comments
    DROP COLUMN last_seen_iteration;

-- devlog_attachments

ALTER TABLE devlog_attachments
    ADD COLUMN first_seen INT NOT NULL,
    ADD COLUMN last_seen  INT NOT NULL;

ALTER TABLE devlog_attachments
    ADD CONSTRAINT fk_devlog_attachments_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE devlog_attachments
    ADD CONSTRAINT fk_devlog_attachments_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

-- devlogs

ALTER TABLE devlogs DROP COLUMN first_seen;
AlTer TABLE devlogs ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE devlogs DROP COLUMN last_seen;
AlTer TABLE devlogs ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE devlogs DROP COLUMN last_requested;
AlTer TABLE devlogs ADD COLUMN last_requested INT;

ALTER TABLE devlogs
    ADD CONSTRAINT fk_devlogs_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE devlogs
    ADD CONSTRAINT fk_devlogs_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE devlogs
    ADD CONSTRAINT fk_devlogs_last_requested__id
        FOREIGN KEY (last_requested) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE devlogs
    DROP COLUMN last_seen_iteration,
    DROP COLUMN last_requested_iteration;

-- projects

ALTER TABLE projects DROP COLUMN first_seen;
AlTer TABLE projects ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE projects DROP COLUMN last_requested;
AlTer TABLE projects ADD COLUMN last_requested INT NOT NULL;

ALTER TABLE projects
    ALTER COLUMN last_requested SET NOT NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_last_requested__id
        FOREIGN KEY (last_requested) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE projects
    DROP COLUMN last_requested_iteration;

-- project_followers

ALTER TABLE project_followers DROP COLUMN first_seen;
AlTer TABLE project_followers ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE project_followers DROP COLUMN last_seen;
AlTer TABLE project_followers ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE project_followers
    ADD CONSTRAINT fk_project_followers_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE project_followers
    ADD CONSTRAINT fk_project_followers_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE project_followers
    DROP COLUMN last_seen_iteration;

-- ship_events

ALTER TABLE ship_events DROP COLUMN first_seen;
AlTer TABLE ship_events ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE ship_events DROP COLUMN last_seen;
AlTer TABLE ship_events ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE ship_events
    ALTER COLUMN last_seen SET NOT NULL;

ALTER TABLE ship_events
    ADD CONSTRAINT fk_ship_events_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE ship_events
    ADD CONSTRAINT fk_ship_events_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE ship_events
    DROP COLUMN last_seen_iteration;

-- superstars

ALTER TABLE superstars DROP COLUMN first_seen;
AlTer TABLE superstars ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE superstars DROP COLUMN last_seen;
AlTer TABLE superstars ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE superstars
    ALTER COLUMN last_seen SET NOT NULL;

ALTER TABLE superstars
    ADD CONSTRAINT fk_superstars_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE superstars
    ADD CONSTRAINT fk_superstars_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE superstars
    DROP COLUMN last_seen_iteration;

-- user_followers

ALTER TABLE user_followers DROP COLUMN first_seen;
AlTer TABLE user_followers ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE user_followers DROP COLUMN last_seen;
AlTer TABLE user_followers ADD COLUMN last_seen INT NOT NULL;

ALTER TABLE user_followers
    ADD CONSTRAINT fk_user_followers_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE user_followers
    ADD CONSTRAINT fk_user_followers_last_seen__id
        FOREIGN KEY (last_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE user_followers
    DROP COLUMN last_seen_iteration;

-- users

ALTER TABLE users DROP COLUMN first_seen;
AlTer TABLE users ADD COLUMN first_seen INT NOT NULL;

ALTER TABLE users DROP COLUMN last_requested;
AlTer TABLE users ADD COLUMN last_requested INT NOT NULL;

ALTER TABLE users
    ADD COLUMN internal_id INT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_first_seen__id
        FOREIGN KEY (first_seen) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_last_requested__id
        FOREIGN KEY (last_requested) REFERENCES requests (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE users
    DROP COLUMN last_requested_iteration;