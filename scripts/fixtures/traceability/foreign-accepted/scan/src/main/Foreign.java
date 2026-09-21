// A foreign-qualified token in a file that is inside no feature directory: DOC-001/FR-777 names a
// requirement of another document. No spec here defines that id, so a gate reading the token as the bare id
// it wraps would report it; and DOC-001's trailing three digits are part of the qualifier, never a feature.
class Foreign {}
