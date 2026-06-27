package com.example.pstarchive.search;

import java.nio.file.Path;
import java.util.List;

public interface CandidateSearchEngine {
    List<SearchCandidate> search(Path storePath, NormalizedQuery query, List<SearchField> fields, int limit) throws Exception;

    String name();
}
