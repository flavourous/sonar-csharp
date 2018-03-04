/*
 * SonarSource :: .NET :: Shared library
 * Copyright (C) 2014-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.dotnet.shared.sarif;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonarsource.dotnet.shared.plugins.SarifParserCallbackImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class SarifParserCallbackImplTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private SensorContextTester ctx;
  private Map<String, String> repositoryKeyByRoslynRuleKey = new HashMap<>();

  private SarifParserCallbackImpl callback;

  @Before
  public void setUp() {
    ctx = SensorContextTester.create(temp.getRoot().toPath());
    repositoryKeyByRoslynRuleKey.put("rule1", "rule1");
    repositoryKeyByRoslynRuleKey.put("rule2", "rule2");

    // file needs to have a few lines so that the issue is within it's range
    ctx.fileSystem().add(TestInputFileBuilder.create("module1", "file1")
      .setContents("My file\ncontents\nwith some\n lines")
      .build());

    callback = new SarifParserCallbackImpl(ctx, repositoryKeyByRoslynRuleKey);
  }

  @Test
  public void should_add_project_issues() {
    callback.onProjectIssue("rule1", "msg", null);
    assertThat(ctx.allIssues()).hasSize(1);
    assertThat(ctx.allIssues().iterator().next().primaryLocation().inputComponent().key()).isEqualTo("projectKey");
    assertThat(ctx.allIssues().iterator().next().ruleKey().rule()).isEqualTo("rule1");
  }

  @Test
  public void should_add_file_issues() {
    String absoluteFilePath = temp.getRoot().toPath().resolve("file1").toString();
    callback.onFileIssue("rule1", absoluteFilePath, "msg", null);
    assertThat(ctx.allIssues()).hasSize(1);
    assertThat(ctx.allIssues().iterator().next().primaryLocation().inputComponent().key()).isEqualTo("module1:file1");
    assertThat(ctx.allIssues().iterator().next().ruleKey().rule()).isEqualTo("rule1");
  }

  @Test
  public void should_ignore_file_issue_with_unknown_rule_key() {
    String absoluteFilePath = temp.getRoot().toPath().resolve("file1").toString();
    callback.onFileIssue("rule45", absoluteFilePath, "msg", null);
    assertThat(ctx.allIssues()).isEmpty();
  }

  @Test
  public void should_ignore_file_issue_with_unknown_file() {
    callback.onFileIssue("rule1", "file-unknown", "msg", null);
    assertThat(ctx.allIssues()).isEmpty();
  }

  @Test
  public void should_ignore_project_issue_with_unknown_rule_key() {
    callback.onProjectIssue("rule45", "msg", null);
    assertThat(ctx.allIssues()).isEmpty();
  }

  @Test
  public void should_add_issues() {
    callback.onIssue("rule1", createLocation("file1", 2, 3), Collections.emptyList(), null);
    callback.onIssue("rule2", createLocation("file1", 2, 3), Collections.emptyList(), null);

    assertThat(ctx.allIssues()).extracting("ruleKey").extracting("rule")
      .containsOnly("rule1", "rule2");
  }

  @Test
  public void should_add_issue_with_secondary_location() {
    callback.onIssue("rule1", createLocation("file1", 2, 3), Collections.singletonList(createLocation("file1", 4, 5)), null);

    assertThat(ctx.allIssues()).hasSize(1);

    List<Flow> flows = ctx.allIssues().iterator().next().flows();
    assertThat(flows).hasSize(1);

    List<IssueLocation> locations = flows.get(0).locations();
    assertThat(locations).hasSize(1);

    assertThat(locations.get(0).inputComponent().key()).isEqualTo("module1:file1");
    TextRange textRange = locations.get(0).textRange();
    assertThat(textRange.start().lineOffset()).isEqualTo(5);
    assertThat(textRange.start().line()).isEqualTo(4);
    assertThat(textRange.end().lineOffset()).isEqualTo(6);
    assertThat(textRange.end().line()).isEqualTo(4);
  }

  @Test
  public void should_ignore_repeated_issues() {
    callback.onIssue("rule1", createLocation("file1", 2, 3), Collections.emptyList(), null);
    callback.onIssue("rule1", createLocation("file1", 2, 3), Collections.emptyList(), null);

    assertThat(ctx.allIssues()).hasSize(1);
    assertThat(ctx.allIssues()).extracting("ruleKey").extracting("rule")
      .containsOnly("rule1");
  }

  private Location createLocation(String filePath, int line, int column) {
    return createLocation(filePath, line, column, line, column + 1);
  }

  private Location createLocation(String filePath, int startLine, int startColumn, int endLine, int endColumn) {
    return new Location(temp.getRoot().toPath().resolve(filePath).toString(), "msg", startLine, startColumn, endLine, endColumn);
  }

}
