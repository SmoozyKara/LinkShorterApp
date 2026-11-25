package shortener.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class State {
    private final Set<UUID> users;
    private final Set<String> issued;
    private final Map<String, Link> links;
    private final List<Notice> notices;

    public State(Set<UUID> users, Set<String> issued, Map<String, Link> links, List<Notice> notices) {
        this.users = users;
        this.issued = issued;
        this.links = links;
        this.notices = notices;
    }

    public Set<UUID> getUsers() {
        return users;
    }

    public Set<String> getIssued() {
        return issued;
    }

    public Map<String, Link> getLinks() {
        return links;
    }

    public List<Notice> getNotices() {
        return notices;
    }

    public static State empty() {
        return new State(new HashSet<>(), new HashSet<>(), new HashMap<>(), new ArrayList<>());
    }

    public static class Notice {
        private final UUID owner;
        private final String message;

        public Notice(UUID owner, String message) {
            this.owner = owner;
            this.message = message;
        }

        public UUID getOwner() {
            return owner;
        }

        public String getMessage() {
            return message;
        }
    }
}
