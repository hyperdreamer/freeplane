package org.freeplane.plugin.graph.workspace.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.namespace.QName;

public final class UnknownXml {
    public enum Owner {
        WORKSPACE,
        MAPS,
        RELATIONSHIPS,
        PINS,
        RECORD
    }

    public enum Kind {
        ATTRIBUTE,
        ELEMENT
    }

    private static final Comparator<QName> QNAME_ORDER = new Comparator<QName>() {
        @Override
        public int compare(final QName first, final QName second) {
            return compareQNames(first, second);
        }
    };
    private static final Map<QName, String> EMPTY_ATTRIBUTES =
        Collections.unmodifiableMap(new TreeMap<QName, String>(QNAME_ORDER));
    private static final List<Content> EMPTY_CONTENT = Collections.emptyList();
    private static final Comparator<UnknownXml> ORDER = new Comparator<UnknownXml>() {
        @Override
        public int compare(final UnknownXml first, final UnknownXml second) {
            int result = Integer.compare(first.owner.ordinal(), second.owner.ordinal());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(kindOrder(first.kind), kindOrder(second.kind));
            if (result != 0) {
                return result;
            }
            if (first.kind == Kind.ATTRIBUTE) {
                return compareQNames(first.name, second.name);
            }
            result = Integer.compare(first.position, second.position);
            if (result != 0) {
                return result;
            }
            return compareQNames(first.name, second.name);
        }
    };

    private final Owner owner;
    private final Kind kind;
    private final int position;
    private final QName name;
    private final String attributeValue;
    private final Map<QName, String> attributes;
    private final List<Content> content;

    private UnknownXml(final Owner owner, final Kind kind, final int position, final QName name,
            final String attributeValue, final Map<QName, String> attributes, final List<Content> content) {
        this.owner = owner;
        this.kind = kind;
        this.position = position;
        this.name = name;
        this.attributeValue = attributeValue;
        this.attributes = attributes;
        this.content = content;
    }

    public static UnknownXml attribute(final Owner owner, final QName name, final String value) {
        return new UnknownXml(
            Objects.requireNonNull(owner, "owner"), Kind.ATTRIBUTE, -1,
            Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"),
            EMPTY_ATTRIBUTES, EMPTY_CONTENT);
    }

    public static UnknownXml element(final Owner owner, final int position, final QName name,
            final Map<QName, String> attributes, final List<Content> content) {
        if (position < 0) {
            throw new IllegalArgumentException("Element position must not be negative");
        }
        return new UnknownXml(
            Objects.requireNonNull(owner, "owner"), Kind.ELEMENT, position,
            Objects.requireNonNull(name, "name"), null,
            copyAttributes(attributes), copyContent(content));
    }

    public Owner owner() {
        return owner;
    }

    public Kind kind() {
        return kind;
    }

    public int position() {
        return position;
    }

    public QName name() {
        return name;
    }

    public Optional<String> attributeValue() {
        return Optional.ofNullable(attributeValue);
    }

    public Map<QName, String> attributes() {
        return attributes;
    }

    public List<Content> content() {
        return content;
    }

    static List<UnknownXml> forRecord(final List<UnknownXml> values) {
        return normalize(values, EnumSet.of(Owner.RECORD), "record unknown XML");
    }

    static List<UnknownXml> forContainers(final List<UnknownXml> values) {
        return normalize(values, EnumSet.of(Owner.WORKSPACE, Owner.MAPS, Owner.RELATIONSHIPS, Owner.PINS),
            "workspace unknown XML");
    }

    private static List<UnknownXml> normalize(final List<UnknownXml> values, final Set<Owner> allowedOwners,
            final String description) {
        Objects.requireNonNull(values, description);
        Objects.requireNonNull(allowedOwners, "allowedOwners");
        final List<UnknownXml> copy = new ArrayList<UnknownXml>(values.size());
        final Map<Owner, Set<QName>> attributeNames = new EnumMap<Owner, Set<QName>>(Owner.class);
        final Map<Owner, Set<Integer>> elementPositions = new EnumMap<Owner, Set<Integer>>(Owner.class);
        for (final UnknownXml value : values) {
            Objects.requireNonNull(value, description + " entry");
            if (!allowedOwners.contains(value.owner)) {
                throw new IllegalArgumentException("Unknown XML owner is not valid here: " + value.owner);
            }
            if (value.kind == Kind.ATTRIBUTE) {
                Set<QName> names = attributeNames.get(value.owner);
                if (names == null) {
                    names = new HashSet<QName>();
                    attributeNames.put(value.owner, names);
                }
                if (!names.add(value.name)) {
                    throw new IllegalArgumentException("Duplicate unknown XML attribute");
                }
            } else {
                Set<Integer> positions = elementPositions.get(value.owner);
                if (positions == null) {
                    positions = new HashSet<Integer>();
                    elementPositions.put(value.owner, positions);
                }
                if (!positions.add(value.position)) {
                    throw new IllegalArgumentException("Duplicate unknown XML element position");
                }
            }
            copy.add(value);
        }
        Collections.sort(copy, ORDER);
        return Collections.unmodifiableList(copy);
    }

    private static Map<QName, String> copyAttributes(final Map<QName, String> values) {
        Objects.requireNonNull(values, "attributes");
        final TreeMap<QName, String> sorted = new TreeMap<QName, String>(QNAME_ORDER);
        for (final Map.Entry<QName, String> entry : values.entrySet()) {
            final QName name = Objects.requireNonNull(entry.getKey(), "attribute name");
            final String value = Objects.requireNonNull(entry.getValue(), "attribute value");
            sorted.put(name, value);
        }
        return Collections.unmodifiableMap(sorted);
    }

    private static List<Content> copyContent(final List<Content> values) {
        Objects.requireNonNull(values, "content");
        final List<Content> copy = new ArrayList<Content>(values.size());
        for (final Content value : values) {
            copy.add(Objects.requireNonNull(value, "content entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static int kindOrder(final Kind kind) {
        return kind == Kind.ATTRIBUTE ? 0 : 1;
    }

    private static int compareQNames(final QName first, final QName second) {
        int result = first.getNamespaceURI().compareTo(second.getNamespaceURI());
        if (result != 0) {
            return result;
        }
        result = first.getLocalPart().compareTo(second.getLocalPart());
        if (result != 0) {
            return result;
        }
        return first.getPrefix().compareTo(second.getPrefix());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnknownXml)) {
            return false;
        }
        final UnknownXml that = (UnknownXml) other;
        return position == that.position && owner == that.owner && kind == that.kind
            && name.equals(that.name) && Objects.equals(attributeValue, that.attributeValue)
            && attributes.equals(that.attributes) && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, kind, position, name, attributeValue, attributes, content);
    }

    @Override
    public String toString() {
        return "UnknownXml{" + "owner=" + owner + ", kind=" + kind + ", position=" + position
            + ", name=" + name + ", attributeValue=" + attributeValue
            + ", attributes=" + attributes + ", content=" + content + '}';
    }

    public static final class Content {
        public enum Kind {
            TEXT,
            ELEMENT
        }

        private final Kind kind;
        private final String text;
        private final QName name;
        private final Map<QName, String> attributes;
        private final List<Content> content;

        private Content(final Kind kind, final String text, final QName name,
                final Map<QName, String> attributes, final List<Content> content) {
            this.kind = kind;
            this.text = text;
            this.name = name;
            this.attributes = attributes;
            this.content = content;
        }

        public static Content text(final String value) {
            return new Content(Kind.TEXT, Objects.requireNonNull(value, "value"), null,
                EMPTY_ATTRIBUTES, EMPTY_CONTENT);
        }

        public static Content element(final QName name, final Map<QName, String> attributes,
                final List<Content> content) {
            return new Content(Kind.ELEMENT, null, Objects.requireNonNull(name, "name"),
                copyAttributes(attributes), copyContent(content));
        }

        public Kind kind() {
            return kind;
        }

        public Optional<String> text() {
            return Optional.ofNullable(text);
        }

        public Optional<QName> name() {
            return Optional.ofNullable(name);
        }

        public Map<QName, String> attributes() {
            return attributes;
        }

        public List<Content> content() {
            return content;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Content)) {
                return false;
            }
            final Content that = (Content) other;
            return kind == that.kind && Objects.equals(text, that.text) && Objects.equals(name, that.name)
                && attributes.equals(that.attributes) && content.equals(that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, text, name, attributes, content);
        }

        @Override
        public String toString() {
            return "Content{" + "kind=" + kind + ", text=" + text + ", name=" + name
                + ", attributes=" + attributes + ", content=" + content + '}';
        }
    }
}
