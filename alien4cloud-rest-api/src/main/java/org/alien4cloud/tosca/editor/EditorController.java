package org.alien4cloud.tosca.editor;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.validation.Valid;

import org.alien4cloud.tosca.editor.model.EditionConcurrencyException;
import org.alien4cloud.tosca.editor.operations.AbstractEditorOperation;
import org.alien4cloud.tosca.editor.operations.UpdateFileOperation;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.io.Closeables;

import alien4cloud.component.repository.IFileRepository;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.topology.TopologyDTO;
import io.swagger.annotations.ApiOperation;

/**
 * Controller endpoint for topology edition.
 */
@RestController
@RequestMapping({ "/rest/v2/editor", "/rest/latest/editor" })
public class EditorController {
    @Inject
    private TopologyEditorService editorService;
    /** We use the artifact repository to store temporary files from the edition context. */
    @Resource
    private IFileRepository artifactRepository;

    /**
     * Execute an operation on a topology.
     *
     * @param topologyId The id of the topology/archive under edition.
     * @param operation The operation to execute
     */
    @ApiOperation(value = "Updates the deployment artifact of the node template.", notes = "The logged-in user must have the application manager role for this application. Application role required [ APPLICATION_MANAGER | APPLICATION_DEVOPS ]")
    @RequestMapping(value = "/{topologyId}/execute", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public RestResponse<TopologyDTO> execute(@PathVariable String topologyId, @RequestBody @Valid AbstractEditorOperation operation) {
        TopologyDTO topologyDTO = editorService.execute(topologyId, operation);
        return RestResponseBuilder.<TopologyDTO> builder().data(topologyDTO).build();
    }

    /**
     * Method exposed to REST to upload a file in an archive under edition.
     * 
     * @param topologyId The id of the topology/archive under edition.
     * @param previousOperationId The id of the user last known operation (for optimistic locking edition).
     * @param path The path in which to save/override the file in the archive.
     * @param file The file to save in the archive.
     */
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/{topologyId}/upload", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public RestResponse<TopologyDTO> upload(@PathVariable String topologyId, @RequestParam("previousOperationId") String previousOperationId,
            @RequestParam("path") String path, @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        // The controller saves the file in a temporary location and create a UpdateFileOperation to be sent in the edition context.
        String artifactFileId = null;
        if (file != null) {
            InputStream artifactStream = file.getInputStream();
            try {
                artifactFileId = artifactRepository.storeFile(artifactStream);
            } finally {
                Closeables.close(artifactStream, true);
            }
        }
        try {
            UpdateFileOperation updateFileOperation = new UpdateFileOperation(path, artifactFileId);
            updateFileOperation.setPreviousOperationId(previousOperationId);
            TopologyDTO topologyDTO = editorService.execute(topologyId, updateFileOperation);
            return RestResponseBuilder.<TopologyDTO> builder().data(topologyDTO).build();
        } catch (EditionConcurrencyException e) {
            // Failed to perform the operation for concurrency issue, delete the temporary file.
            if (artifactFileId != null) {
                artifactRepository.deleteFile(artifactFileId);
            }
            throw e;
        }
    }

    /**
     * Download a temporary file which is not yet commited (uploaded or modified through an operation).
     * 
     * @param topologyId The if of the topology.
     * @param artifactId The id of the temporary artifact.
     * @return The response entity with the input stream of the file.
     */
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/{topologyId}/file/{artifactId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputStreamResource> downloadTempFile(@PathVariable String topologyId, @PathVariable String artifactId) {
        editorService.checkAuthorization(topologyId);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        long length = artifactRepository.getFileLength(artifactId);

        return ResponseEntity.ok().headers(headers).contentLength(length).contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(new InputStreamResource(artifactRepository.getFile(artifactId)));
    }

    /**
     * Save the given topology and commit to the local git repository.
     *
     * @param topologyId The id of the topology/archive under edition to save.
     */
    public void save(@PathVariable String topologyId) {
        // Call the service that will save and commit

    }

    /**
     * Undo or redo operations so that we reach the given index in the operation stack.
     *
     * @param topologyId The id of the topology/archive under edition to save.
     * @param at the index of the operation to go to.
     */
    public void undoRedo(@PathVariable String topologyId, int at, String lastOperationId) {
        // Call the service that will save and commit

    }
}