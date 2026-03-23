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
        A a = Verifier.nondetObject(A.class, null);
        // assertion violation reachable
        // NPE reachable
        if (a.getX() == 0) {
            assert false : "getX()";
        }
    }
}
