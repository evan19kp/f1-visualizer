package com.evanp.f1.core.stint;

import java.util.List;
import java.util.Optional;

public interface StintStore {

    void save(long sessionKey, List<StintSnapshot> stints);

    Optional<StintSnapshot> getLatest(long sessionKey, int driverNumber);

    List<StintSnapshot> getAll(long sessionKey);
}
