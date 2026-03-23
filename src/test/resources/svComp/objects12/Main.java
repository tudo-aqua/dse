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
        A a1 = Verifier.nondetObject(A.class, null);
        A a2 = Verifier.nondetObject(A.class, null);
        // assertion violation reachable
        // NPE reachable
        if (a1.getX() == a2.getX()) {
            assert false;
        }
    }

}
