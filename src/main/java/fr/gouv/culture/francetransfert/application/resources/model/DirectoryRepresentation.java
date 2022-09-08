/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DirectoryRepresentation extends DataRepresentation {

    private long totalSize;

    @Valid
    private List<FileRepresentation> files;

    @Valid
    private List<DirectoryRepresentation> dirs;
}
