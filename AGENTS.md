# Coding conventions

## Prefer explicit over clever

Code gets read far more often than it is written. Pick the version a reader
understands on the first pass, even when that version is a line longer.

- **No ternaries (`? :`) in this codebase.** Write an `if` with an early
  `return` instead. A ternary saves a line but makes the reader evaluate the
  condition inline, and it turns unreadable fast once nested or long.
- Keep one statement per line. Do not cram several steps into one expression to
  look tidy.
- If a construct needs a comment to justify it, it should be simpler.

Example — `S3Storage.extensionOf`:

```java
// avoid: a ternary inside another expression, three things to read at once
var key = UUID.randomUUID() + "."
    + (subtype.contains("+") ? subtype.substring(0, subtype.indexOf('+')) : subtype);

// prefer: the helper says what it returns, the caller says what it does
var key = UUID.randomUUID() + "." + extensionOf(contentType);

private static String extensionOf(String contentType) {
    var subtype = MimeTypeUtils.parseMimeType(contentType).getSubtype();
    var plusIndex = subtype.indexOf("+");
    if (plusIndex < 0) {
        return subtype;
    }
    return subtype.substring(0, plusIndex);
}
```

Spend the extra lines. Clever is not the same as short, and short is not the
same as clear.

## Other rules

- Follow the surrounding style (4-space indent in `src/main`, `var` for locals,
  `record` for DTOs). Consistency beats personal preference.
- Existing patterns in the repo win over new ones you prefer.
