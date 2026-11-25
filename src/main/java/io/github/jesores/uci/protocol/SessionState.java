package io.github.jesores.uci.protocol;

enum SessionState {
    UNSTARTED,
    HANDSHAKING_UCI,
    HANDSHAKING_READY,
    IDLE,
    SEARCHING,
    STOPPING,
    CLOSING,
    CLOSED
}
