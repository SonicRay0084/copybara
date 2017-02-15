/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@code Origin} represents a source control repository from which source is copied.
 *
 * @param <R> the origin type of the references/revisions this origin handles
 */
@SkylarkModule(
    name = "origin",
    doc = "A Origin represents a source control repository from which source is copied.",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
public interface Origin<R extends Revision> extends ConfigItemDescription {

  /**
   * Resolves a migration reference into a revision. For example for git it would resolve 'master'
   * to the SHA-1.
   *
   * @throws RepoException if any error happens during the resolve.
   */
  //TODO(malcon): change String to Reference. But the change is massive for this change.
  R resolve(String reference) throws RepoException, ValidationException;

  /**
   * Given a request for a migration from the command line, resolve to an object that represents
   * what to migrate.
   *
   * @param args commandline arguments
   */
  default Reference<R> migrationReference(List<String> args) {
    // TODO(malcon): Implement this in the origins, but I want to show the intention of the
    // refactor.
    return null;
  }

  /**
   * An object which is capable of checking out code from the origin at particular paths. This can
   * also enumerate changes in the history and transform authorship information.
   */
  interface Reader<R extends Revision> extends ChangeVisitable<R> {

    /**
     * Checks out the revision {@code ref} from the repository into {@code workdir} directory. This
     * method is not on {@link Revision} in order to prevent {@link Destination} implementations
     * from getting access to the code pre-transformation.
     *
     * @throws RepoException if any error happens during the checkout or workdir preparation.
     */
    void checkout(R ref, Path workdir) throws RepoException, ValidationException;

    /**
     * Returns the changes that happen in the interval (fromRef, toRef].
     *
     * <p>If {@code fromRef} is null, returns all the changes from the first commit of the parent
     * branch to {@code toRef}, both included.
     *
     * @param fromRef the revision used in the latest invocation. If null it means that no
     * previous ref could be found or that the destination didn't store the ref.
     * @param toRef current revision to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    ImmutableList<Change<R>> changes(@Nullable R fromRef, R toRef) throws RepoException;

    /**
     * Returns true if the origin repository supports maintaining a history of changes. Generally
     * this should be true
     */
    default boolean supportsHistory() {
      return true;
    }

    /**
     * Returns a change identified by {@code ref}.
     *
     * @param ref current revision to transform.
     * @throws RepoException if any error happens during the computation of the diff.
     */
    Change<R> change(R ref) throws RepoException;

  }

  /**
   * Creates a new reader of this origin.
   *
   * @param originFiles indicates which files in the origin repository need to be read. Note that
   *     the reader does not necessarily need to remove files after checking them out according to
   *     the glob - that is actually done automatically by the {@link Workflow}. However, some
   *     {@link Origin} implementations may choose to optimize operations on the repo based on the
   *     glob.
   * @param authoring the authoring object used for constructing the Author objects.
   * @throws ValidationException if the reader could not be created because of a user error. For
   *     instance, the origin cannot be used with the given {@code originFiles}.
   */
  Reader<R> newReader(Glob originFiles, Authoring authoring) throws ValidationException;

  /**
   * Label name to be used in when creating a commit message in the destination to refer to a
   * revision. For example "Git-RevId".
   */
  String getLabelName();

  /**
   * A reference to a version that can have a meaningful name and that can mutate (For example
   * 'master' SHA-1 can change).
   *
   * It can contain an optional revision object to pin the reference to an specific revision.
   */
  class Reference<R> {

    private final String reference;
    @Nullable
    private final R revision;
    private final ImmutableMap<String, String> labels;

    public Reference(String reference, R revision, ImmutableMap<String, String> labels) {
      this.reference = Preconditions.checkNotNull(reference);
      this.labels = Preconditions.checkNotNull(labels);
      this.revision = revision;
    }

    /**
     * The named reference to migrate
     */
    public String getReference() {
      return reference;
    }

    /**
     * An optional revision of the reference. Otherwise when resolved it will be resolved to the
     * HEAD of that reference.
     */
    @Nullable
    public R getRevision() {
      return revision;
    }

    /**
     * Additional labels that the origin wants to make available to the workflow with information
     * about the reference. For example this could be the code review url for git.
     */
    public ImmutableMap<String, String> getLabels() {
      return labels;
    }
  }
}
