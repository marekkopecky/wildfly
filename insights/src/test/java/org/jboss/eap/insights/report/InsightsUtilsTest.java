/*
 * Copyright 2023 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.eap.insights.report;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InsightsUtilsTest {

    private final String input;
    private final String expected;

    public InsightsUtilsTest(String input, String expected, String description) {
        this.input = input;
        this.expected = expected;
    }

    @Parameters(name = "{2}: \"{0}\" -> \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"myapp", "myapp", "clean name unchanged"},
            {"my-app_v1.0", "my-app_v1.0", "dots hyphens underscores preserved"},
            {"my app", "my_app", "spaces replaced"},
            {"app@name#here!", "app_name_here_", "special characters replaced"},
            {"path/to\\app", "path_to_app", "path separators replaced"},
            {"C:/app", "C__app", "colons replaced"},
            {"MyApp", "MyApp", "mixed case preserved"},
            {"", "", "empty string"},
            {"/tmp/greeter", "_tmp_greeter", "/tmp/greeter.war prefix"},
            {"ROOT", "ROOT", "ROOT.war prefix"},
        });
    }

    @Test
    public void testSanitizeFolderName() {
        assertEquals(expected, InsightsUtils.sanitizeFolderName(input));
    }
}
