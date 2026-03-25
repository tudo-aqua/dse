/*
 * Contributed to SV-COMP by Marvin Lazar and Falk Howar
 * License: MIT (see /java/objects/LICENSE-MIT)
 *
 * SPDX-FileCopyrightText: 2025 Marvin Lazar and Falk Howar, TU Dortmund University
 * SPDX-FileCopyrightText: 2025 The SV-Benchmarks Community
 * SPDX-License-Identifier: MIT
 */

import tools.aqua.concolic.Verifier;

public class Main {
    public static void main(String[] args) {
        Any o = Verifier.nondetObject(Any.class, new Factories.AnyFactory());
        // assertion violation reachable
        if (o == null) {
            assert false;
        }
    }
}
