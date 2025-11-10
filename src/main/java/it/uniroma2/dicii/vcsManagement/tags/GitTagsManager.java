package it.uniroma2.dicii.vcsManagement.tags;

import it.uniroma2.dicii.properties.PropertiesManager;
import it.uniroma2.dicii.vcsManagement.exception.TagRetrievalException;
import it.uniroma2.dicii.vcsManagement.model.Tag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@Slf4j
public class GitTagsManager {

    private static final String TAG_PREFIX = "release-";

    private final Repository repository;

    @Getter
    private List<Tag> tags;

    public GitTagsManager() throws IOException {
        String repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(repoPath + "/.git")).readEnvironment().findGitDir().build();
    }

    /**
     * Retrieves all the tags from the git repository and saves them in the tag list.
     */
    public void retrieveTags() throws TagRetrievalException {
        try (Git git = new Git(repository); RevWalk walk = new RevWalk(repository)) {
            // Retrieves all the tags from the repository
            List<Ref> refs = git.tagList().call();
            this.tags = new ArrayList<>();

            // Iterate through all tags and extract commit IDs from each one
            for (Ref ref : refs) {
                String simpleName = Repository.shortenRefName(ref.getName());
                if (!simpleName.toLowerCase(Locale.ROOT).startsWith(TAG_PREFIX)) {
                    log.warn("Tag {} does not start with prefix {}", simpleName, TAG_PREFIX);
                    continue;
                }

                // Peel annotated tags, i.e., for annotated tags, retrieving the associated Commit;
                // if the tag is not annotated (lightweight), the associated commit is the same as
                // the commit that the tag points to
                Ref peeled = repository.getRefDatabase().peel(ref);
                ObjectId tagObjectId = ref.getObjectId();
                ObjectId targetId = peeled.getPeeledObjectId() != null
                        ? peeled.getPeeledObjectId()
                        : tagObjectId;

                RevObject obj = walk.parseCommit(targetId);
                String commitId = obj.getId().getName();

                log.debug("Tag {} points to commit {}", simpleName, commitId);
                tags.add(new Tag(simpleName, commitId));
            }
        } catch (Exception e) {
            throw new TagRetrievalException("Failed to retrieve tags from repository", e);
        }
    }

    /**
     * Finds a tag by its version name. If the version name is not found, returns null.
     * This method finds tags with the tag format "release-versionName".
     *
     * @param versionName   the version name of the tag to be found
     * @return              the tag with the given version name, or null if no tag is found
     */
    public Tag findTagByVersionName(String versionName) {
        if (versionName == null || versionName.isBlank() || this.tags.isEmpty()) return null;

        String tagName = TAG_PREFIX + versionName;
        return this.tags.stream().filter(t -> t.getTagName().equals(tagName)).findFirst().orElse(null);
    }

}
