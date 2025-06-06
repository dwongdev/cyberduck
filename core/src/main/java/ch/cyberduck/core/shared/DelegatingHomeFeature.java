package ch.cyberduck.core.shared;

/*
 * Copyright (c) 2002-2021 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Home;

public class DelegatingHomeFeature implements Home {

    private final Home[] features;

    public DelegatingHomeFeature(final Home... features) {
        this.features = features;
    }

    @Override
    public Path find() throws BackgroundException {
        for(Home feature : features) {
            if(null == feature) {
                // No native implementation
                continue;
            }
            final Path home = feature.find();
            if(null == home) {
                continue;
            }
            return home;
        }
        return Home.root();
    }
}
