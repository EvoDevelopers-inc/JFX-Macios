package evo.developers.ru.macios.permissions;

/** Authorization state of a macOS privacy-protected resource. */
public enum PermissionStatus {
    /** The user has not yet been asked. */
    NOT_DETERMINED,
    /** Access granted. */
    GRANTED,
    /** The user explicitly denied access. */
    DENIED,
    /** Access disallowed by policy (parental controls / MDM). */
    RESTRICTED,
    /** Status could not be determined synchronously (query asynchronously instead). */
    UNKNOWN
}
