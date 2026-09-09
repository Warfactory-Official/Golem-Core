// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

public interface SectionTrackedVisual {

    void collectCrumblingSections(SectionCollector collector);

    @FunctionalInterface
    interface SectionCollector {
        void accept(long sectionKey);
    }
}
//?}
