/**
 * Modern JavaScript frontend for Softcite Software Recognition
 * Refactored for better maintainability and modern patterns
 * 
 * Author: Refactored from original by Patrice Lopez
 */

class SoftciteApp {
    constructor() {
        this.supportedLanguages = ["en"];
        this.entities = null;
        this.conceptMap = new Map();
        this.entityMap = new Map();
        this.referenceMap = new Map();
        this.responseJson = null;

        // Example texts
        this.examples = [
            "The column scores (the fraction of entirely correct columns) were  reported  in  addition  " +
            "to Q-scores  for  BAliBASE 3.0. Wilcoxon  signed-ranks  tests  were  performed  to  calculate statistical " +
            " significance  of  comparisons  between  alignment programs,   which   include   ProbCons   (version   1.10)" +
            "   (23), MAFFT (version 5.667) (11) with several options, MUSCLE (version 3.52) (10) and ClustalW (version 1.83) (7).",
            
            "Sequences were further annotated with PERL scripts using BioPerl libraries (21) together with data and libraries from Ensemble (22) (Supplementary Table 1 and Supplementary Perl scripts 1-3). Repetitive sequences were identified using repeatmasker (http://www.repeatmasker.org). Spike control amplicons were prepared by PCR from DNA extracted from normal blood. ",
            
            "All statistical analyses were done using computer software Prism 6 for Windows (version 6.02; GraphPad Software, San Diego, CA, USA). One-Way ANOVA was used to detect differences amongst the groups. To account for the non-normal distribution of the data, all data were sorted by rank status prior to ANOVA statistical analysis. ",
            
            "The statistical analysis was performed using IBM SPSS Statistics v. 20 (SPSS Inc, 2003, Chicago, USA)."
        ];

        // PDF examples
        this.examplesPDF = ["PMC3130168", "PMC2773253", "econ01"];
    }

    init() {
        this.setupEventListeners();
        this.initializeUI();
        this.setupBaseURL();
        this.configurePdfJs();
    }

    setupEventListeners() {
        this.bindNavigationEvents();
        this.bindFormEvents();
        this.bindExampleEvents();
    }

    bindNavigationEvents() {
        $("#about").click(() => {
            this.showSection('about');
            return false;
        });

        $("#textTab").click(() => {
            this.showSection('text');
            return false;
        });

        $("#pdfTab").click(() => {
            this.showSection('pdf');
            return false;
        });

        $("#doc").click(() => {
            this.showSection('doc');
            return false;
        });
    }

    bindFormEvents() {
        $('#submitRequest').bind('click', () => this.submitTextQuery());
        $('#submitRequest2').bind('click', () => this.submitPDFQuery());
    }

    bindExampleEvents() {
        this.examples.forEach((example, index) => {
            $(`#example${index}`).bind('click', (event) => {
            event.preventDefault();
                $('#inputTextArea').val(example);
                this.resetExamplesClasses();
                $(`#example${index}`).removeClass('section-non-active').addClass('section-active');
            });
        });
    }

    showSection(section) {
        // Hide all sections
        $("#divAbout, #divRestI, #divRestII, #divDoc").hide();
        $("#toggle-group, #pure-toggle-right").hide();
        $("#pure-toggle-right").prop('checked', false);

        // Reset navigation classes
        $("#about, #textTab, #pdfTab, #doc").attr('class', 'section-not-active');

        switch(section) {
            case 'about':
            $("#about").attr('class', 'section-active');
                $("#subTitle").html("About").show();
            $("#divAbout").show();
                break;
            case 'text':
            $("#textTab").attr('class', 'section-active');
            $("#subTitle").hide();
            $("#divRestI").show();
                this.processChange();
                break;
            case 'pdf':
            $("#pdfTab").attr('class', 'section-active');
            $("#subTitle").hide();
            $("#divRestII").show();
                this.processChange();
            if ($('#mention-count').is(":visible")) {
                    $("#toggle-group, #pure-toggle-right").show();
                    $("#pure-toggle-right").prop('checked', true);
                }
                break;
            case 'doc':
            $("#doc").attr('class', 'section-active');
                $("#subTitle").html("Doc").show();
            $("#divDoc").show();
                break;
        }

            if ($('#mention-count-drawer').is(":visible")) {
                $("#toggle-group").click();
        }
    }

    initializeUI() {
        $("#subTitle").html("About");
        $("#divAbout").show();
        $("#divRestI, #divRestII, #divDoc").hide();
        $("#toggle-group, #pure-toggle-right").hide();
        $("#pure-toggle-right").prop('checked', false);

        this.createInputTextArea();
        this.setBaseUrl('processSoftwareText');
    }

    setupBaseURL() {
        // Initialize base URL setup
        this.setBaseUrl('processSoftwareText');
    }

    configurePdfJs() {
        try {
            if (window.pdfjsLib && window.pdfjsLib.GlobalWorkerOptions) {
                if (!window.pdfjsLib.GlobalWorkerOptions.workerSrc) {
                    window.pdfjsLib.GlobalWorkerOptions.workerSrc = 'resources/pdf.js/build/pdf.worker.js';
                }
            }
            if (window.PDFJS && !window.PDFJS.workerSrc) {
                window.PDFJS.workerSrc = 'resources/pdf.js/build/pdf.worker.js';
            }
        } catch (e) {
            // ignore configuration errors
        }
    }

    defineBaseURL(ext) {
        let localBase = $(location).attr('href');
        if (localBase.indexOf("index.html") !== -1) {
            localBase = localBase.replace("index.html", "");
        } 
        if (localBase.endsWith("#")) {
            localBase = localBase.substring(0, localBase.length - 1);
        } 
        if (localBase.indexOf("?") !== -1) {
            localBase = localBase.substring(0, localBase.indexOf("?"));
        } 
        return localBase + "service/" + ext;
    }

    setBaseUrl(ext) {
        const baseUrl = this.defineBaseURL(ext);
        $('#gbdForm').attr('action', baseUrl);
        $('#gbdForm2').attr('action', baseUrl);
    }

    createInputTextArea() {
        $('#fileInputDiv').hide();
        $('#textInputDiv').show();
    }

    createInputFile() {
        $('#textInputDiv').hide();
        $('#fileInputDiv').show();
        $('#gbdForm2').attr('enctype', 'multipart/form-data').attr('method', 'post');
        this.resetExamplesClasses();
        this.setupPDFExamples();
    }

    // Initialize the tabbed UI for PDF results: Annotations and Response (JSON)
    initializePdfTabs() {
        const tabsHTML = `
            <div class="note-tabs">
                <ul id="pdfResultTab" class="nav nav-tabs">
                    <li class="active"><a href="#pdf-annotation-tab" data-toggle="tab">Annotations</a></li>
                    <li><a href="#pdf-json-tab" data-toggle="tab">Response</a></li>
                </ul>
                <div class="tab-content">
                    <div class="tab-pane active" id="pdf-annotation-tab">
                        <div id="pdfAnnotationContainer"></div>
                    </div>
                    <div class="tab-pane" id="pdf-json-tab">
                        <pre class="prettyprint lang-json" id="pdf-json-pre"></pre>
                    </div>
                </div>
            </div>`;
        $('#requestResult2').html(tabsHTML);
        const pre = document.getElementById('pdf-json-pre');
        if (pre) pre.textContent = 'Waiting for server response...';
    }

    setupPDFExamples() {
        if (this.isEmpty($('#examples_pdf'))) {
            const examplesHTML = this.examplesPDF.map((example, index) => 
                `<tr style='line-height:130%;'>
                    <td>
                        <span style='font-size:90%;'>
                            <a id='example_pdf${index}' href='#' data-toggle='modal' data-target='#confirm-process'>
                                ${example}
                            </a>
                        </span>
                    </td>
                </tr>`
            ).join('');
            
            $('#examples_pdf').append(`<table id="withExamples">${examplesHTML}</table>`);
            
            this.examplesPDF.forEach((example, index) => {
                $(`#example_pdf${index}`).click((e) => {
                    e.preventDefault();
                    $('#name_pdf_example').html(example);
                    this.resetExamplesClasses();
                    $(`#example_pdf${index}`).removeClass('section-non-active').addClass('section-active');
                });
            });
            
            $('#validate-process').click(() => {
                const selectedPDF = $('#name_pdf_example').text();
                this.setJsonExamplePDF(selectedPDF);
            });
        }
    }

    resetExamplesClasses() {
        this.examples.forEach((_, index) => {
            $(`#example${index}`).removeClass('section-active').addClass('section-non-active');
        });
        this.examplesPDF.forEach((_, index) => {
            $(`#example_pdf${index}`).removeClass('section-active').addClass('section-non-active');
        });
    }

    processChange() {
        if ($("#divRestI").is(":visible")) {
            this.setBaseUrl('processSoftwareText');
            this.createInputTextArea();
        } else if ($("#divRestII").is(":visible")) {
            this.setBaseUrl('annotateSoftwarePDF');
            this.createInputFile();
        }
    }

    submitTextQuery() {
        this.resetMaps();
        $("#pure-toggle-right, #toggle-group").hide();
            
            $('#infoResult').html('<span style="color: grey;">Requesting server...</span>');
            $('#requestResult').html('');

        const urlLocal = $('#gbdForm').attr('action');
        const disambiguateVal = $("#disambiguate").is(":checked") ? '1' : '0';
            
            $.ajax({
                type: 'GET',
                url: urlLocal,
                data: { text: $('#inputTextArea').val(), disambiguate: disambiguateVal },
                dataType: 'json',
                success: (response) => this.handleTextResponse(response),
                error: (jqXHR) => this.handleAjaxError(jqXHR)
        });
    }

    submitPDFQuery() {
        this.resetMaps();
        $("#pure-toggle-right, #toggle-group").hide();
        this.resetExamplesClasses();
        
        $('#infoResult2').empty().html('<span style="color: grey;">Requesting server...</span>');
        // initialize tabbed result area for PDF
        this.initializePdfTabs();

        const form = document.getElementById('gbdForm2');
        const formData = new FormData(form);
        
        if (!$("#disambiguate2").is(":checked")) {
                formData.append('disambiguate', '0');
        } else {
            formData.append('disambiguate', '1');
        }

        const xhr = new XMLHttpRequest();
        const url = $('#gbdForm2').attr('action');
            xhr.responseType = 'json'; 
            xhr.open('POST', url, true);
        
        const file = document.getElementById("input").files[0];
        if (this.isValidPDFFile(file)) {
            this.processPDFFile(file, xhr, formData);
        } else {
            this.handleAjaxError2("This does not look like a PDF");
        }
    }

    resetMaps() {
        this.entityMap.clear();
        this.conceptMap.clear();
        this.referenceMap.clear();
    }

    isValidPDFFile(file) {
        return file && (
            file.type === 'application/pdf' ||
            file.name.endsWith(".pdf") ||
            file.name.endsWith(".PDF")
        );
    }

    processPDFFile(file, xhr, formData) {
        const reader = new FileReader();
        reader.onloadend = () => {
            const pdfAsArray = new Uint8Array(reader.result);
            // Support both legacy PDFJS and newer pdfjsLib APIs
            const getDocument = (window.PDFJS && window.PDFJS.getDocument)
                ? window.PDFJS.getDocument
                : (window.pdfjsLib && window.pdfjsLib.getDocument ? window.pdfjsLib.getDocument : null);
            if (!getDocument) {
                this.handleAjaxError2('PDF viewer is not available.');
                return;
            }
            try {
                const task = getDocument(pdfAsArray);
                if (task && task.promise && typeof task.promise.then === 'function') {
                    task.promise.then((pdf) => this.renderPDFPages(pdf));
                } else if (task && typeof task.then === 'function') {
                    task.then((pdf) => this.renderPDFPages(pdf));
                } else {
                    this.handleAjaxError2('Unable to load PDF document.');
                }
            } catch (e) {
                this.handleAjaxError2('Error while loading PDF: ' + (e && e.message ? e.message : e));
            }
        };
        reader.readAsArrayBuffer(file);
        
        xhr.onreadystatechange = (e) => {
            if (xhr.readyState === 4 && xhr.status === 200) {
                const response = e.target.response;
                this.setupAnnotations(response);
            } else if (xhr.readyState === 4 && xhr.status !== 200) {
                this.handleAjaxError2(`Response ${xhr.status}: `);
            }
        };
        xhr.send(formData);
    }

    renderPDFPages(pdf) {
        const container = document.getElementById("pdfAnnotationContainer") || document.getElementById("requestResult2");
        const nbPages = pdf.numPages;
        
        for (let i = 1; i <= nbPages; i++) {
            pdf.getPage(i).then((page) => {
                this.renderPDFPage(page, nbPages, container);
            });
        }
    }

    renderPDFPage(page, nbPages, container) {
        const table = document.createElement("table");
        table.setAttribute('style', 'table-layout: fixed; width: 100%;');
        
        const tr = document.createElement("tr");
        const td1 = document.createElement("td");
        const td2 = document.createElement("td");

        tr.appendChild(td1);
        tr.appendChild(td2);
        table.appendChild(tr);

        // Page info
        const div0 = document.createElement("div");
        div0.setAttribute("style", "text-align: center; margin-top: 1cm;");
        const pageInfo = document.createElement("p");
        pageInfo.appendChild(document.createTextNode(`page ${page.pageIndex + 1}/${nbPages}`));
        div0.appendChild(pageInfo);
        td1.appendChild(div0);

        // PDF page container
        const div = document.createElement("div");
        div.setAttribute("id", `page-${page.pageIndex + 1}`);
        div.setAttribute("style", "position: relative;");
        
        const canvas = document.createElement("canvas");
        canvas.setAttribute("style", "border-style: solid; border-width: 1px; border-color: gray;");
        div.appendChild(canvas);

        td1.setAttribute('style', 'width:70%;');
        td1.appendChild(div);

        // Annotation container (right column)
        const annot = document.createElement("div");
        annot.setAttribute('id', `detailed_annot-${page.pageIndex + 1}`);
        // This panel will be absolutely positioned within the right column
        annot.setAttribute('style', 'position:absolute; left:0; right:0;');
        td2.setAttribute('style', 'vertical-align:top;width:30%; position:relative;');
        td2.appendChild(annot);

        container.appendChild(table);

        // Render PDF
        const viewport = page.getViewport((td1.offsetWidth * 0.98) / page.getViewport(1.0).width);
        const context = canvas.getContext('2d');
        canvas.height = viewport.height;
        canvas.width = viewport.width;

        // Make right column height follow the page header + canvas height for reliable positioning
        const headerH = div0 ? div0.offsetHeight : 0;
        td2.style.height = `${headerH + viewport.height}px`;
        td2.style.position = 'relative';

        const renderContext = { canvasContext: context, viewport: viewport };

        page.render(renderContext).then(() => {
            return page.getTextContent();
        }).then((textContent) => {
            this.createTextLayer(div, textContent, page.pageIndex, viewport);
        });
    }

    createTextLayer(div, textContent, pageIndex, viewport) {
        const textLayerDiv = document.createElement("div");
                                    textLayerDiv.setAttribute("class", "textLayer");
                                    div.appendChild(textLayerDiv);

        const textLayer = new TextLayerBuilder({
                                        textLayerDiv: textLayerDiv,
            pageIndex: pageIndex,
                                        viewport: viewport
                                    });

                                    textLayer.setTextContent(textContent);
                                    textLayer.render();
    }

    handleTextResponse(responseText) {
        this.responseJson = responseText;
        if (!this.responseJson) {
            $('#infoResult').html("<span style='color:red;'>Error encountered while receiving the server's answer: response is empty.</span>");
            return;
        }
        
        // Populate referenceMap if available in text response
        this.referenceMap.clear();
        if (this.responseJson.references && Array.isArray(this.responseJson.references)) {
            this.responseJson.references.forEach((reference) => {
                if (reference && reference.refKey && reference.tei) {
                    this.referenceMap.set(reference.refKey, reference.tei);
                }
            });
        }

        $('#infoResult').html('');
        // Response is already parsed as JSON by jQuery AJAX
        this.displayTextResults();
    }

    displayTextResults() {
        const display = this.createResultsHTML();
        $('#requestResult').html(display);
        window.prettyPrint && prettyPrint();
        
        if (this.responseJson.mentions) {
            this.bindEntityEvents();
        }
        
        $('#detailed_annot-0').hide();
        $('#requestResult').show();
    }

    createResultsHTML() {
        let display = `
            <div class="note-tabs">
                <ul id="resultTab" class="nav nav-tabs">
                    <li class="active"><a href="#navbar-fixed-annotation" data-toggle="tab">Annotations</a></li>
                    <li><a href="#navbar-fixed-json" data-toggle="tab">Response</a></li>
                </ul>
                <div class="tab-content">
                    <div class="tab-pane active" id="navbar-fixed-annotation">
        `;

        display += '<pre style="background-color:#FFF;width:95%;" id="displayAnnotatedText">';
        display += this.createAnnotatedText();
        display += '</pre>';
        display += '</div>';
        display += '<div class="tab-pane" id="navbar-fixed-json">';
        display += "<pre class='prettyprint lang-json' id='xmlCode'>";
        // Ensure pretty-printed JSON string is escaped for HTML display
        const prettyJson = vkbeautify.json(JSON.stringify(this.responseJson));
        display += this.escapeHtml(prettyJson);
        display += "</pre>";
        display += '</div></div></div>';
        
        return display;
    }

    createAnnotatedText() {
        const string = $('#inputTextArea').val();
        let newString = "";
        let pos = 0;
        
        let display = '<table id="sentence" style="width:100%;table-layout:fixed;" class="table">';
        display += '<tr style="background-color:#FFF;">';
        
        this.entities = this.responseJson.mentions;
        const lang = this.responseJson.lang || 'en';
        
        if (this.entities) {
            this.entities.forEach((entity, currentEntityIndex) => {
                const pieces = this.extractEntityPieces(entity, lang);
                pieces.sort((a, b) => parseInt(a.offsetStart, 10) - parseInt(b.offsetStart, 10));
                
                pieces.forEach((piece, pi) => {
                    const start = parseInt(piece.offsetStart, 10);
                    const end = parseInt(piece.offsetEnd, 10);
                    
                    if (start >= pos) {
                        newString += string.substring(pos, start);
                        newString += `<span id="annot-${currentEntityIndex}-${pi}" class="label ${piece.subtype}" style="cursor:hand;cursor:pointer;">`;
                        newString += string.substring(start, end) + '</span>';
                        pos = end;
                    }
                });
            });
        }
        
        newString += string.substring(pos, string.length);
        newString = "<p>" + newString.replace(/(\r\n|\n|\r)/gm, "</p><p>") + "</p>";
        
        display += '<td style="font-size:small;width:60%;border:1px solid #CCC;"><p>' + newString + '</p></td>';
        display += '<td style="font-size:small;width:40%;padding:0 5px; border:0"><span id="detailed_annot-0" /></td>';
        display += '</tr>';
        display += '</table>';
        
        return display;
    }

    extractEntityPieces(entity, lang) {
        const pieces = [];
        
        // Software name
        const softwareName = entity['software-name'];
        if (softwareName) {
            softwareName.subtype = 'software';
            pieces.push(softwareName);
        }
        
        // Version
        const version = entity['version'];
                if (version) {
            version.subtype = 'version';
            pieces.push(version);
                }

        // URL
        const softwareUrl = entity['url'];
                if (softwareUrl) {
            softwareUrl.subtype = 'url';
            pieces.push(softwareUrl);
                }

        // Publisher
        const creator = entity['publisher'];
                if (creator) {
            creator.subtype = 'publisher';
            pieces.push(creator);
                }

        // Language
        const language = entity['language'];
                if (language) {
            language.subtype = 'language';
            pieces.push(language);
                }

        // References
        const references = entity['references'];
                if (references) {
            references.forEach(reference => {
                reference.subtype = 'reference';
                if (!reference.rawForm) {
                    reference.rawForm = reference.label;
                }
                pieces.push(reference);
            });
        }
        
        return pieces;
    }

    bindEntityEvents() {
        this.entities.forEach((entity, entityIndex) => {
            const indexComp = this.countEntityComponents(entity);
            for (let currentIndexComp = 0; currentIndexComp < indexComp; currentIndexComp++) {
                $(`#annot-${entityIndex}-${currentIndexComp}`)
                    .on('mouseenter click', (e) => this.viewEntity(e));
            }
        });
    }

    countEntityComponents(entity) {
        let count = 0;
        if (entity['software-name']) count++;
        if (entity['version']) count++;
        if (entity['url']) count++;
        if (entity['publisher']) count++;
        if (entity['language']) count++;
        if (entity['references']) count += entity['references'].length;
        return count;
    }

    viewEntity(event) {
        if (!this.responseJson || !this.responseJson.mentions) return;
        
        const localID = $(event.target).attr('id');
        const ind1 = localID.indexOf('-');
        const ind2 = localID.indexOf('-', ind1 + 1);
        const localEntityNumber = parseInt(localID.substring(ind1 + 1, ind2));
        
        if (localEntityNumber < this.entities.length) {
            const entity = this.entities[localEntityNumber];
            const wikipediaId = entity && entity.wikipediaExternalRef;
            const lang = (entity && entity.lang) ? entity.lang : 'en';
            const render = () => {
                const string = this.toHtml(entity, -1, 0);
                $('#detailed_annot-0').html(string).show();
            };
            if (wikipediaId && !this.conceptMap.has(wikipediaId)) {
                this.fetchConcept(wikipediaId, lang, (result) => {
                    if (result && result.wikipediaExternalRef) {
                        this.conceptMap.set(result.wikipediaExternalRef, result);
                    }
                    render();
                });
            } else {
                render();
            }
        }
    }

    setupAnnotations(response) {
        if ((response == null) || (response.length == 0)) {
            $('#infoResult2').html("<span style='color:red;'>Error encountered while receiving the server's answer: response is empty.</span>");
            return;
        } else {
            $('#infoResult2').empty().hide();
        }

        const json = response;
        // store as current response and populate JSON tab if present
        this.responseJson = json;
        const pdfJsonPre = document.getElementById('pdf-json-pre');
        if (pdfJsonPre) {
            try {
                const pretty = vkbeautify.json(JSON.stringify(json));
                // Use textContent to avoid HTML injection
                pdfJsonPre.textContent = pretty;
                window.prettyPrint && prettyPrint();
            } catch (e) {
                pdfJsonPre.textContent = JSON.stringify(json, null, 2);
            }
        }

        const pageInfo = json.pages;
        let page_height = 0.0;
        let page_width = 0.0;

        this.entities = json.mentions;
        if (this.entities) {
            this.entities.forEach((entity, n) => {
                this.entityMap.set(n, [entity]);

                const identifier = entity.wikipediaExternalRef;
                const wikidataId = entity.wikidataId;
                
                let localLang = 'en';
                if (entity.lang) {
                    localLang = entity.lang;
                }

                if (identifier && !this.conceptMap.has(identifier)) {
                    this.fetchConcept(identifier, localLang, (result) => {
                        this.conceptMap.set(result.wikipediaExternalRef, result);
                    });
                }

                const pieces = [];
                const softwareName = entity['software-name'];
                softwareName.subtype = 'software';
                pieces.push(softwareName);

                const version = entity['version'];
                if (version) {
                    version.subtype = 'version';
                    pieces.push(version);
                }

                const softwareUrl = entity['url'];
                if (softwareUrl) {
                    softwareUrl.subtype = 'url';
                    pieces.push(softwareUrl);
                }

                const creator = entity['publisher'];
                if (creator) {
                    creator.subtype = 'publisher';
                    pieces.push(creator);
                }

                const language = entity['language'];
                if (language) {
                    language.subtype = 'language';
                    pieces.push(language);
                }

                const references = entity['references'];
                if (references) {
                    references.forEach((reference, r) => {
                        reference.subtype = 'reference';
                        if (!reference.rawForm) {
                            reference.rawForm = reference.label;
                        }
                        pieces.push(reference);
                    });
                }

                pieces.sort((a, b) => {
                    const startA = parseInt(a.offsetStart, 10);
                    const startB = parseInt(b.offsetStart, 10);
                    return startA - startB;
                });

                const type = entity.type;
                const id = entity.id;
                pieces.forEach((piece, pi) => {
                    const pos = piece.boundingBoxes;
                    const rawForm = softwareName.rawForm;
                    if ((pos != null) && (pos.length > 0)) {
                        pos.forEach((thePos, m) => {
                            const pageNumber = thePos.p;
                            if (pageInfo[pageNumber-1]) {
                                page_height = pageInfo[pageNumber-1].page_height;
                                page_width = pageInfo[pageNumber-1].page_width;
                            }
                            this.annotateEntity(id, rawForm, piece.subtype, thePos, page_height, page_width, n, pi+m);
                        });
                }
                });
            });
        }

        const references = json.references;
        if (references) {
            references.forEach((reference, n) => {
                this.referenceMap.set(reference.refKey, reference.tei);
            });
        }

        this.displaySummary(response);
        $('#infoResult2').show();
    }

    displaySummary(response) {
        $('#infoResult2').empty();
        this.entities = response.mentions;
        
        // Get page canvas width for visual alignment
        let width = "70%";
        if ($("canvas").length > 0) {
            width = $("canvas").first().width();
        }
        // Compute a numeric pixel width for the drawer and summary containers
        let widthPx = 0;
        if (typeof width === 'number') {
            widthPx = width;
        } else {
            // default to 70% of viewport width, clamped
            const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
            widthPx = Math.min(1000, Math.max(480, Math.floor(vw * 0.7)));
        }

        if (this.entities) {
            let summary = '';
            summary += `<div id='mention-count' style='background-color: white; width: 100%;'><p>&nbsp;&nbsp;<b>${this.entities.length}</b> mentions found</p></div>`;

            summary += `<table width='${width}px' style='table-layout: fixed; overflow: scroll; padding-left:5px; width:${width}%;'>`;
            summary += '<colgroup><col span="1" style="width: 20%;"><col span="1" style="width: 5%;"><col span="1" style="width: 55%;"><col span="1" style="width: 20%;"></colgroup>';

            const local_map = new Map();
            const usage_map = new Map();

            this.entities.forEach((entity, n) => {
                let local_page = -1;
                if (entity['software-name'].boundingBoxes && entity['software-name'].boundingBoxes.length > 0) {
                    local_page = entity['software-name'].boundingBoxes[0].p;
                }
                let the_id = 'annot-' + n + '-0';
                if (local_page != -1) {
                    the_id += '_' + local_page;
                }
                const softwareNameRaw = entity['software-name'].normalizedForm;
                if (!local_map.has(softwareNameRaw)) {
                    local_map.set(softwareNameRaw, []);
                }
                const localArray = local_map.get(softwareNameRaw);
                localArray.push(the_id);
                local_map.set(softwareNameRaw, localArray);

                if (entity['documentContextAttributes']) {
                    usage_map.set(softwareNameRaw, entity['documentContextAttributes']);
                }
            });

            const span_ids = [];

            let n = 0;
            for (let [key, value] of local_map) {
                summary += `<tr width='${width}' style='background: `;
                if (n % 2 == 0) {
                    summary += "#eee;'>";
                } else {
                    summary += "#fff;'>";
                }
                summary += `<td>${key}</td>`;
                summary += `<td>${value.length}</td>`;
                summary += `<td style='display: inline-block; word-break: break-word;' >`;

                value.sort((a, b) => {
                    let a_page = -1;
                    if (a.indexOf("_") != -1) {
                        const local_page = a.substring(a.indexOf("_") + 1);
                        a_page = parseInt(local_page);
                        if (isNaN(a_page)) {
                            a_page = -1;
                        }
                    }

                    let b_page = -1;
                    if (b.indexOf("_") != -1) {
                        const local_page = b.substring(b.indexOf("_") + 1);
                        b_page = parseInt(local_page);
                        if (isNaN(b_page)) {
                            b_page = -1;
                        }
                    }

                    if (a_page < b_page) {
                        return -1;
                    }
                    if (a_page > b_page) {
                        return 1;
                    }
                    return 0;
                });

                for (let i = 0; i < value.length; i++) {
                    const the_id_full = value[i];
                    let the_id = the_id_full;
                    let local_page = the_id_full;
                    if (the_id_full.indexOf("_") != -1) {
                        the_id = the_id_full.substring(0, the_id_full.indexOf("_"));
                        local_page = the_id_full.substring(the_id_full.indexOf("_"));
                    }
                    const pageNum = ('' + local_page).startsWith('_') ? ('' + local_page).substring(1) : ('' + local_page);
                    // Extract entity index from the_id: annot-<entity>-<pos>
                    let entityIdx = null;
                    const parts = the_id.split('-');
                    if (parts.length >= 3) {
                        entityIdx = parts[1];
                    }
                    summary += `<span class='index' id='index_${the_id}' data-target='${the_id}' data-page='${pageNum}' data-entity='${entityIdx}'>page${local_page}</span> `;
                    span_ids.push('index_' + the_id);
                }

                let attributesInfo = "";
                if (usage_map.get(key)) {
                    const documentAttributes = usage_map.get(key);
                    if (documentAttributes.used.value) {
                        attributesInfo += "used";
                    }
                    if (documentAttributes.created.value) {
                        attributesInfo += " created";
                    }
                    if (documentAttributes.shared.value) {
                        attributesInfo += " shared";
                    }
                }

                summary += `<td>${attributesInfo}</td></tr>`;
                n++;
            }

            summary += "</table>";
            summary += "</div>";

            $('#infoResult2').html(`<div style="width: ${widthPx}px; border-style: solid; border-width: 1px; border-color: gray; background-color: white;">${summary}`);

            // Setup bigSlide functionality
            $('#toggle-group').bigSlide({
                side: 'right',
                menu: '#drawer',
                menuWidth: `${widthPx}px`,
                afterOpen: () => {
                    $("#pure-toggle-right").off('click').on('click', () => {
                        $("#toggle-group").click();
                    });
                    $('#drawer').show();
                },
                afterClose: () => {
                    $('#drawer').hide();
                }
            });

            // Ensure the top-right toggle icon always opens/closes the drawer
            $("#pure-toggle-right").off('click').on('click', () => {
                $("#toggle-group").click();
            });

            for (let span_index in span_ids) {
                summary = summary.replace(span_ids[span_index], span_ids[span_index] + "-drawer");
            }
            $('#drawer').html(`<div style="width: ${widthPx}px; border-style: solid; border-width: 1px; border-color: gray; background-color: white; margin: auto;">${summary.replace('mention-count', 'mention-count-drawer')}`);

            $("#pure-toggle-right").show();
            $("#toggle-group").show();

            // Bind click events for navigation
            for (let span_index in span_ids) {
                const span_id = span_ids[span_index];
                $(`#${span_id}`).bind('click', (event) => {
                    const $el = $(event.currentTarget);
                    const baseTargetId = $el.data('target') || $el.attr('id').replace('index_', '');
                    const pageNum = $el.data('page');
                    const entityIdx = $el.data('entity');
                    let local_target = document.getElementById(baseTargetId);
                    if ((!local_target || !pageNum) && pageNum) {
                        const pageContainer = document.getElementById(`page-${pageNum}`);
                        if (pageContainer) {
                            // Prefer software anchors for that entity on that page
                            if (entityIdx !== undefined && entityIdx !== null) {
                                local_target = pageContainer.querySelector(`[id^='annot-${entityIdx}-'].software`)
                                    || pageContainer.querySelector(`[id^='annot-${entityIdx}-']`);
                            }
                            // As a last resort, scroll to the page container itself
                            if (!local_target) local_target = pageContainer;
                        }
                    }
                    if (!local_target) return;
                    const topPos = local_target.getBoundingClientRect().top + window.pageYOffset - 100;

                    window.scrollTo({ top: topPos, behavior: 'smooth' });
                    if (local_target.id && local_target.id.startsWith('annot-')) {
                        local_target.click();
                    }
                });

                $(`#${span_id}-drawer`).bind('click', (event) => {
                    const $el = $(event.currentTarget);
                    const baseTargetId = $el.data('target') || $el.attr('id').replace('index_', '').replace("-drawer", "");
                    const pageNum = $el.data('page');
                    const entityIdx = $el.data('entity');
                    let local_target = document.getElementById(baseTargetId);
                    if ((!local_target || !pageNum) && pageNum) {
                        const pageContainer = document.getElementById(`page-${pageNum}`);
                        if (pageContainer) {
                            // Prefer software anchors for that entity on that page
                            if (entityIdx !== undefined && entityIdx !== null) {
                                local_target = pageContainer.querySelector(`[id^='annot-${entityIdx}-'].software`)
                                    || pageContainer.querySelector(`[id^='annot-${entityIdx}-']`);
                            }
                            // As a last resort, scroll to the page container itself
                            if (!local_target) local_target = pageContainer;
                        }
                    }
                    if (!local_target) return;
                    const topPos = local_target.getBoundingClientRect().top + window.pageYOffset - 100;

                    window.scrollTo({ top: topPos, behavior: 'smooth' });
                    if (local_target.id && local_target.id.startsWith('annot-')) {
                        local_target.click();
                    }
                    $("#toggle-group").click();
                });
            }
        }
    }

    toHtml(entity, topPos, pageIndex) {
        const wikipedia = entity.wikipediaExternalRef;
        const wikidataId = entity.wikidataId;
        const type = entity.type;
        const softwareType = entity["software-type"];

        let colorLabel = null;
        if (type) {
            colorLabel = type;
        }

        const definitions = this.getDefinitions(wikipedia);
        let lang = null;
        if (entity.lang) {
            lang = entity.lang;
        }

        const content = entity['software-name'].rawForm;
        const normalized = this.getPreferredTerm(wikipedia);

        // Remove inline positioning; we'll position the panel container instead
        let string = `<div class='info-sense-box ${colorLabel}'>`;
        string += `<h4 style='color:#FFF;padding-left:10px;'>${content.toUpperCase()}</h4>`;
        string += `<div class='container-fluid' style='background-color:#F9F9F9;color:#70695C;border:padding:5px;margin-top:5px;'>`;
        string += `<table style='width:100%;background-color:#fff;border:0px'><tr style='background-color:#fff;border:0px;'><td style='background-color:#fff;border:0px;'>`;

        if (type) {
            string += `<p>Entity type: <b>${type}</b></p>`;
        }

        if (softwareType) {
            string += `<p>Software type: <b>${softwareType}</b></p>`;
        }

        if (content) {
            string += `<p>Raw name: <b>${content}</b></p>`;
        }

        if (normalized) {
            string += `<p>Normalized name: <b>${normalized}</b></p>`;
        }

        const versionNumber = entity['version-number'];
        if (versionNumber) {
            string += `<p>Version nb: <b>${versionNumber.rawForm}</b></p>`;
        }

        const versionDate = entity['version-date'];
        if (versionDate) {
            string += `<p>Version date: <b>${versionDate.rawForm}</b></p>`;
        }

        const version = entity['version'];
        if (version) {
            string += `<p>Version: <b>${version.rawForm}</b></p>`;
        }

        const language = entity['language'];
        if (language) {
            string += `<p>Language: <b>${language.rawForm}</b></p>`;
        }

        const url = entity['url'];
        if (url) {
            let urlValue = url.rawForm.trim();
            if (!urlValue.startsWith('http://') && !urlValue.startsWith('https://')) {
                urlValue = 'http://' + urlValue;
            }
            string += `<p>URL: <b><a href="${urlValue}" target="_blank">${urlValue}</a></b></p>`;
        }

        const creator = entity['publisher'];
        if (creator) {
            string += `<p>Publisher: <b>${creator.rawForm}</b></p>`;
        }

        if (entity.confidence) {
            string += `<p>Confidence: <i>${entity.confidence}</i></p>`;
        }

        if (wikipedia) {
            string += "</td><td style='align:right;bgcolor:#fff'>";
            string += `<span id="img-${wikipedia}-${pageIndex}"><script type="text/javascript">lookupWikiMediaImage("${wikipedia}", "${lang}", "${pageIndex}")</script></span>`;
        }

        string += "</td></tr></table>";

        if (entity.mentionContextAttributes || entity.documentContextAttributes) {
            string += "<br/><div style='width:100%;background-color:#fff;border:0px'>";

            if (entity.mentionContextAttributes) {
                if (entity.mentionContextAttributes.used.value || entity.mentionContextAttributes.created.value || entity.mentionContextAttributes.shared.value) {
                    string += "<p>Mention-level: ";
                }

                if (entity.mentionContextAttributes.used.value) {
                    string += `<b>used</b> (<i>${entity.mentionContextAttributes.used.score.toFixed(3)}</i>)`;
                }

                if (entity.mentionContextAttributes.created.value) {
                    string += ` - <b>created</b> (<i>${entity.mentionContextAttributes.created.score.toFixed(3)}</i>)`;
                }

                if (entity.mentionContextAttributes.shared.value) {
                    string += ` - <b>shared</b> (<i>${entity.mentionContextAttributes.shared.score.toFixed(3)}</i>)`;
                }

                if (entity.mentionContextAttributes.used.value) {
                    string += "</p>";
                }
            }

            if (entity.documentContextAttributes) {
                if (entity.documentContextAttributes.used.value || entity.documentContextAttributes.created.value || entity.documentContextAttributes.shared.value) {
                    string += "<p>Document-level: ";
                }

                if (entity.documentContextAttributes.used.value) {
                    string += `<b>used</b> (<i>${entity.documentContextAttributes.used.score.toFixed(3)}</i>)`;
                }

                if (entity.documentContextAttributes.created.value) {
                    string += ` - <b>created</b> (<i>${entity.documentContextAttributes.created.score.toFixed(3)}</i>)`;
                }

                if (entity.documentContextAttributes.shared.value) {
                    string += ` - <b>shared</b> (<i>${entity.documentContextAttributes.shared.score.toFixed(3)}</i>)`;
                }

                if (entity.documentContextAttributes.used.value) {
                    string += "</p>";
                }
            }
            
            string += "</div>";
        }

        // References
        if (entity['references']) {
            string += "<br/><p>References: ";
            entity['references'].forEach((reference, r) => {
                if (reference['label']) {
                    let localLabel = "";
                    let localHtml = "";
                    if (reference['refKey'] && this.referenceMap.has(reference['refKey'])) {
                        const doc = this.parse(this.referenceMap.get(reference['refKey']));
                        const authors = doc.getElementsByTagName("author");
                        let max = authors.length;
                        if (max > 3) {
                            max = 1;
                        }
                        for (let n = 0; n < authors.length; n++) {
                            let lastName = "";
                            let firstName = "";
                            const localNodes = authors[n].getElementsByTagName("surname");
                            if (localNodes && localNodes.length > 0) {
                                lastName = localNodes[0].childNodes[0].nodeValue;
                            }
                            const forenameNodes = authors[n].getElementsByTagName("forename");
                            if (forenameNodes && forenameNodes.length > 0) {
                                firstName = forenameNodes[0].childNodes[0].nodeValue;
                            }
                            if (n == 0 && authors.length > 3) {
                                localLabel += lastName + " et al";
                            } else if (n == 0) {
                                localLabel += lastName;
                            } else if (n == max - 1 && authors.length <= 3) {
                                localLabel += " & " + lastName;
                            } else if (authors.length <= 3) { 
                                localLabel += ", " + lastName;
                            }
                        }

                        const dateNodes = doc.evaluate("//date[@type='published']/@when", doc, null, XPathResult.ANY_TYPE, null);
                        const date = dateNodes.iterateNext();
                        if (date) {
                            const ind = date.textContent.indexOf("-");
                            let year = date.textContent;
                            if (ind != -1) {
                                year = year.substring(0, ind);
                            }
                            localLabel += " (" + year + ")";
                        }

                        localHtml += this.displayBiblio(doc);
                    }

                    // Make the biblio reference information collapsible
                    string += `<p><div class='panel-group' id='accordionParentReferences-${pageIndex}-${r}'>`;
                    string += "<div class='panel panel-default'>";
                    string += "<div class='panel-heading' style='background-color:#FFF;color:#70695C;border:padding:0px;font-size:small;'>";
                    string += `<a class='accordion-toggle collapsed' data-toggle='collapse' data-parent='#accordionParentReferences-${pageIndex}-${r}' href='#collapseElementReferences-${pageIndex}-${r}' style='outline:0;'>`;
                    string += `<h5 class='panel-title' style='font-weight:normal;'>${reference['label']} ${localLabel}</h5>`;
                    string += "</a>";
                    string += "</div>";
                    string += `<div id='collapseElementReferences-${pageIndex}-${r}' class='panel-collapse collapse'>`;
                    string += "<div class='panel-body'>";
                    string += `<table class='statements' style='width:100%;background-color:#fff;border:1px'>${localHtml}</table>`;
                    string += "</div></div></div></div></p>";
                }
            });
            string += "</p>";
        }

        if ((definitions != null) && (definitions.length > 0)) {
            // Use the external wiki2html utility for proper formatting
            const localHtml = (window.wiki2html) ? window.wiki2html(definitions[0]['definition'], lang) : this.escapeHtml(definitions[0]['definition']);
            string += `<p><div class='wiky_preview_area2'>${localHtml}</div></p>`;
        }

        // Statements
        const statements = this.getStatements(wikipedia);
        if ((statements != null) && (statements.length > 0)) {
            let localHtml = "";
            statements.forEach((statement) => {
                localHtml += this.displayStatement(statement);
            });

            // Make the statements information collapsible
            string += `<p><div class='panel-group' id='accordionParentStatements'>`;
            string += "<div class='panel panel-default'>";
            string += "<div class='panel-heading' style='background-color:#FFF;color:#70695C;border:padding:0px;font-size:small;'>";
            string += `<a class='accordion-toggle collapsed' data-toggle='collapse' data-parent='#accordionParentStatements' href='#collapseElementStatements${pageIndex}' style='outline:0;'>`;
            string += "<h5 class='panel-title' style='font-weight:normal;'>Wikidata statements</h5>";
            string += "</a>";
            string += "</div>";
            string += `<div id='collapseElementStatements${pageIndex}' class='panel-collapse collapse'>`;
            string += "<div class='panel-body'>";
            string += `<table class='statements' style='width:100%;background-color:#fff;border:1px'>${localHtml}</table>`;
            string += "</div></div></div></div></p>";
        }

        if ((wikipedia != null) || (wikidataId != null)) {
            string += '<p>References: ';
            if (wikipedia != null) {
                string += `<a href="http://${lang}.wikipedia.org/wiki?curid=${wikipedia}" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" src="resources/img/wikipedia.png" alt="Wikipedia"/></a>`;
            }
            if (wikidataId != null) {
                string += `<a href="https://www.wikidata.org/wiki/${wikidataId}" target="_blank"><img style="max-width:28px;max-height:22px;margin-top:5px;" src="resources/img/Wikidata-logo.svg" alt="Wikidata"/></a>`;
            }
            string += '</p>';
        }

        string += "</div></div>";
        return string;
    }

    setJsonExamplePDF(example) {
        console.log('Processing PDF example:', example);
        const pdf_url = `resources/pdf-examples/${example.replace("/", "%2F")}.pdf`;
        
        $("#pure-toggle-right, #toggle-group").hide();
        $('#infoResult2').empty().html('<span style="color: grey;">Requesting server...</span>');
        // initialize tabbed result area for PDF
        this.initializePdfTabs();

        const formData = new FormData();
        formData.append('disambiguate', $("#disambiguate2").is(":checked") ? '1' : '0');

        const xhr = new XMLHttpRequest();
        const url = this.defineBaseURL("annotateSoftwarePDF");
        xhr.responseType = 'json';
        xhr.open('POST', url, true);
        
        // Load and process the PDF file: render pages AND send to backend
        this.httpGetAsynBlob(pdf_url, async (data) => {
            try {
                const arrayBuffer = await data.arrayBuffer();
                const pdfAsArray = new Uint8Array(arrayBuffer);
                // Render locally using PDF.js
                const getDocument = (window.PDFJS && window.PDFJS.getDocument)
                    ? window.PDFJS.getDocument
                    : (window.pdfjsLib && window.pdfjsLib.getDocument ? window.pdfjsLib.getDocument : null);
                if (getDocument) {
                    try {
                        const task = getDocument(pdfAsArray);
                        if (task && task.promise && typeof task.promise.then === 'function') {
                            task.promise.then((pdf) => this.renderPDFPages(pdf));
                        } else if (task && typeof task.then === 'function') {
                            task.then((pdf) => this.renderPDFPages(pdf));
                        }
                    } catch (e) {
                        // ignore render errors; backend annotations may still be displayed
                    }
                }
                // Send to backend
                const the_blob = new Blob([data], { type: 'application/pdf' });
                formData.append('input', the_blob, `${example.replace("/", "%2F")}.pdf`);
                xhr.send(formData);
            } catch (err) {
                this.handleAjaxError2('Unable to load PDF example.');
            }
        });

        xhr.onreadystatechange = (e) => {
            if (xhr.readyState === 4 && xhr.status === 200) {
                $('#infoResult2').html("");
                const response = e.target.response;
                this.setupAnnotations(response);
            } else if (xhr.readyState === 4 && xhr.status !== 200) {
                this.handleAjaxError2(`Response ${xhr.status}: `);
            }
        };
    }

    httpGetAsynBlob(theUrl, callback) {
        const xmlHttp = new XMLHttpRequest();
        xmlHttp.responseType = 'blob';
        xmlHttp.onreadystatechange = function() {
            if (xmlHttp.readyState === 4 && xmlHttp.status === 200) {
                callback(xmlHttp.response);
            }
        };
        xmlHttp.open("GET", theUrl, true);
        xmlHttp.send(null);
    }

    annotateEntity(theId, rawForm, theType, thePos, page_height, page_width, entityIndex, positionIndex) {
        const page = thePos.p;
        const tryAppend = (attempt = 0) => {
            const pageDiv = $(`#page-${page}`);
            if (pageDiv.length === 0) {
                if (attempt < 50) {
                    // wait for the page to be rendered
                    return setTimeout(() => tryAppend(attempt + 1), 100);
                }
                // give up silently if page never appears
                return;
            }
            const canvas = pageDiv.children('canvas').eq(0);
            if (canvas.length === 0) {
                if (attempt < 50) {
                    return setTimeout(() => tryAppend(attempt + 1), 100);
                }
                return;
            }

            const canvasHeight = canvas.height();
            const canvasWidth = canvas.width();
            const scale_y = canvasHeight / page_height;
            const scale_x = canvasWidth / page_width;

            const x = (thePos.x * scale_x) - 1;
            const y = (thePos.y * scale_y) - 1;
            const width = (thePos.w * scale_x) + 1;
            const height = (thePos.h * scale_y) + 1;

            // Make clickable the area, ensure it sits above the textLayer
            const element = document.createElement("a");
            const attributes = `display:block; width:${width}px; height:${height}px; position:absolute; top:${y}px; left:${x}px; z-index: 5; pointer-events: auto;`;
            element.setAttribute("style", attributes + "border:2px solid;");
            element.setAttribute("class", theType.toLowerCase());
            element.setAttribute("id", `annot-${entityIndex}-${positionIndex}`);
            element.setAttribute("page", page);

            pageDiv.append(element);

            $(`#annot-${entityIndex}-${positionIndex}`).bind('mouseenter', (e) => this.viewEntityPDF(e));
            $(`#annot-${entityIndex}-${positionIndex}`).bind('click', (e) => this.viewEntityPDF(e));
        };
        tryAppend(0);
    }

    viewEntityPDF(event) {
        const pageIndex = $(event.target).attr('page');
        const localID = $(event.target).attr('id');

        if (this.entities == null) {
            return;
        }

        // Desired vertical position relative to the PDF page
        let desiredTop = $(event.target).position().top;
        try {
            const pageDiv = document.getElementById(`page-${pageIndex}`);
            if (pageDiv && pageDiv.parentElement) {
                const leftTd = pageDiv.parentElement; // the left table cell
                const leftTdTop = leftTd.getBoundingClientRect().top + window.pageYOffset;
                const pageDivTop = pageDiv.getBoundingClientRect().top + window.pageYOffset;
                const headerOffset = pageDivTop - leftTdTop; // space above canvas (page header + margins)
                desiredTop = Math.max(0, headerOffset + desiredTop - 4);
            }
        } catch (e) {
            // keep fallback desiredTop
        }

        const ind1 = localID.indexOf('-');
        const localEntityNumber = parseInt(localID.substring(ind1 + 1, localID.length));

        if (!this.entityMap.has(localEntityNumber) || this.entityMap.get(localEntityNumber).length === 0) {
            console.log("Error for visualising annotation with id " + localEntityNumber + ", empty list of entities");
        }

        let string = "";
        const entityList = this.entityMap.get(localEntityNumber);
        if (entityList && entityList.length > 0) {
            const entity = entityList[entityList.length - 1];
            string = this.toHtml(entity, -1, pageIndex);
        }

        const panel = document.getElementById(`detailed_annot-${pageIndex}`);
        if (!panel) return;
        panel.innerHTML = string;
        panel.style.display = 'block';
        panel.style.position = 'absolute';
        panel.style.left = '0';
        panel.style.right = '0';

        // Clamp within the right column height
        const parent = panel.parentElement;
        const box = panel.firstElementChild;
        const parentH = parent ? parent.clientHeight : 0;
        const boxH = box ? box.offsetHeight : 0;
        let topClamped = desiredTop;
        if (parentH > 0 && boxH > 0) {
            const maxTop = Math.max(0, parentH - boxH - 6);
            topClamped = Math.max(0, Math.min(desiredTop, maxTop));
        }
        panel.style.top = `${topClamped}px`;
        // Keep content visible inside the panel area
        if (parentH > 0) {
            panel.style.maxHeight = `${parentH - 6}px`;
            panel.style.overflow = 'auto';
        }
    }

    fetchConcept(identifier, lang, successFunction) {
        $.ajax({
            type: 'GET',
            url: 'https://cloud.science-miner.com/nerd/service/kb/concept/' + identifier + '?lang=' + lang,
            success: (result) => {
                successFunction(result);
            },
            dataType: 'json'
        });
    }

    // New implementations based on original frontend for feature parity
    getDefinitions(identifier) {
        if (!identifier) return null;
        const localEntity = this.conceptMap.get(identifier);
        return localEntity ? localEntity.definitions : null;
    }

    getPreferredTerm(identifier) {
        if (!identifier) return null;
        const localEntity = this.conceptMap.get(identifier);
        return localEntity ? localEntity.preferredTerm : null;
    }

    getStatements(identifier) {
        if (!identifier) return null;
        const localEntity = this.conceptMap.get(identifier);
        return localEntity ? localEntity.statements : null;
    }

    displayStatement(statement) {
        let localHtml = "";
        if (statement.propertyId) {
            if (statement.propertyName) {
                localHtml += `<tr><td>${statement.propertyName}</td>`;
            } else if (statement.propertyId) {
                localHtml += `<tr><td>${statement.propertyId}</td>`;
            }

            const valueType = statement.valueType;
            if (valueType && (valueType === 'time')) {
                if (statement.value) {
                    const time = statement.value.time;
                    if (time) {
                        const ind = time.indexOf("T");
                        if (ind === -1)
                            localHtml += `<td>${time.substring(1)}</td></tr>`;
                        else
                            localHtml += `<td>${time.substring(1, ind)}</td></tr>`;
                    }
                }
            } else if (valueType && (valueType === 'globe-coordinate')) {
                if (statement.value) {
                    const latitude = statement.value.latitude;
                    const longitude = statement.value.longitude;
                    const precision = statement.value.precision;
                    let gpsString = "";
                    if (latitude) {
                        gpsString += "latitude: " + latitude;
                    }
                    if (longitude) {
                        gpsString += ", longitude: " + longitude;
                    }
                    if (precision) {
                        gpsString += ", precision: " + precision;
                    }
                    localHtml += `<td>${gpsString}</td></tr>`;
                }
            } else if (valueType && (valueType === 'string')) {
                if (statement.propertyId === "P2572") {
                    if (statement.value) {
                        localHtml += `<td><a href='https://twitter.com/hashtag/${statement.value.trim()}?src=hash' target='_blank'>#${statement.value}</a></td></tr>`;
                    } else {
                        localHtml += `<td></td></tr>`;
                    }
                } else {
                    if (statement.value) {
                        localHtml += `<td>${statement.value}</td></tr>`;
                    } else {
                        localHtml += `<td></td></tr>`;
                    }
                }
            } else if (valueType && (valueType === 'url')) {
                if (statement.value) {
                    if (statement.value.startsWith("https://") || statement.value.startsWith("http://")) {
                        localHtml += `<td><a href="${statement.value}" target="_blank">${statement.value}</a></td></tr>`;
                    } else {
                        localHtml += `<td></td></tr>`;
                    }
                }
            } else {
                if (statement.valueName) {
                    localHtml += `<td>${statement.valueName}</td></tr>`;
                } else if (statement.value) {
                    localHtml += `<td>${statement.value}</td></tr>`;
                } else {
                    localHtml += `<td></td></tr>`;
                }
            }
        }
        return localHtml;
    }

    displayBiblio(doc) {
        // authors
        const authors = doc.getElementsByTagName("author");
        let localHtml = "<tr><td>authors</td><td>";
        for (let n = 0; n < authors.length; n++) {
            let lastName = "";
            let localNodes = authors[n].getElementsByTagName("surname");
            if (localNodes && localNodes.length > 0)
                lastName = localNodes[0].childNodes[0].nodeValue;

            const foreNames = [];
            localNodes = authors[n].getElementsByTagName("forename");
            if (localNodes && localNodes.length > 0) {
                for (let m = 0; m < localNodes.length; m++) {
                    foreNames.push(localNodes[m].childNodes[0].nodeValue);
                }
            }

            if (n !== 0)
                localHtml += ", ";
            for (let m = 0; m < foreNames.length; m++) {
                localHtml += foreNames[m];
            }
            localHtml += " " + lastName;
        }
        localHtml += "</td></tr>";

        // article title
        let titleNodes = doc.evaluate("//title[@level='a']", doc, null, XPathResult.ANY_TYPE, null);
        let theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += `<tr><td>title</td><td>${theTitle.textContent}</td></tr>`;
        }

        // date
        let dateNodes = doc.evaluate("//date[@type='published']/@when", doc, null, XPathResult.ANY_TYPE, null);
        let date = dateNodes.iterateNext();
        if (date) {
            localHtml += `<tr><td>date</td><td>${date.textContent}</td></tr>`;
        }


        // journal title
        titleNodes = doc.evaluate("//title[@level='j']", doc, null, XPathResult.ANY_TYPE, null);
        theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += `<tr><td>journal</td><td>${theTitle.textContent}</td></tr>`;
        }

        // monograph title
        titleNodes = doc.evaluate("//title[@level='m']", doc, null, XPathResult.ANY_TYPE, null);
        theTitle = titleNodes.iterateNext();
        if (theTitle) {
            localHtml += `<tr><td>book title</td><td>${theTitle.textContent}</td></tr>`;
        }

        // conference
        let meetingNodes = doc.evaluate("//meeting", doc, null, XPathResult.ANY_TYPE, null);
        let theMeeting = meetingNodes.iterateNext();
        if (theMeeting) {
            localHtml += `<tr><td>conference</td><td>${theMeeting.textContent}</td></tr>`;
        }

        // address
        let addressNodes = doc.evaluate("//address", doc, null, XPathResult.ANY_TYPE, null);
        let theAddress = addressNodes.iterateNext();
        if (theAddress) {
            localHtml += `<tr><td>address</td><td>${theAddress.textContent}</td></tr>`;
        }

        // volume
        let volumeNodes = doc.evaluate("//biblScope[@unit='volume']", doc, null, XPathResult.ANY_TYPE, null);
        let volume = volumeNodes.iterateNext();
        if (volume) {
            localHtml += `<tr><td>volume</td><td>${volume.textContent}</td></tr>`;
        }

        // issue
        let issueNodes = doc.evaluate("//biblScope[@unit='issue']", doc, null, XPathResult.ANY_TYPE, null);
        let issue = issueNodes.iterateNext();
        if (issue) {
            localHtml += `<tr><td>issue</td><td>${issue.textContent}</td></tr>`;
        }

        // pages
        let pageNodes = doc.evaluate("//biblScope[@unit='page']/@from", doc, null, XPathResult.ANY_TYPE, null);
        let firstPage = pageNodes.iterateNext();
        if (firstPage) {
            localHtml += `<tr><td>first page</td><td>${firstPage.textContent}</td></tr>`;
        }
        pageNodes = doc.evaluate("//biblScope[@unit='page']/@to", doc, null, XPathResult.ANY_TYPE, null);
        let lastPage = pageNodes.iterateNext();
        if (lastPage) {
            localHtml += `<tr><td>last page</td><td>${lastPage.textContent}</td></tr>`;
        }
        pageNodes = doc.evaluate("//biblScope[@unit='page']", doc, null, XPathResult.ANY_TYPE, null);
        let pages = pageNodes.iterateNext();
        if (pages && pages.textContent != null && pages.textContent.length > 0) {
            localHtml += `<tr><td>pages</td><td>${pages.textContent}</td></tr>`;
        }

        // issn
        let issnNodes = doc.evaluate("//idno[@type='ISSN']", doc, null, XPathResult.ANY_TYPE, null);
        let issn = issnNodes.iterateNext();
        if (issn) {
            localHtml += `<tr><td>ISSN</td><td>${issn.textContent}</td></tr>`;
        }
        issnNodes = doc.evaluate("//idno[@type='ISSNe']", doc, null, XPathResult.ANY_TYPE, null);
        let issne = issnNodes.iterateNext();
        if (issne) {
            localHtml += `<tr><td>e ISSN</td><td>${issne.textContent}</td></tr>`;
        }

        //doi
        let doiNodes = doc.evaluate("//idno[@type='DOI']", doc, null, XPathResult.ANY_TYPE, null);
        let doi = doiNodes.iterateNext();
        if (doi && doi.textContent) {
            localHtml += `<tr><td>DOI</td><td><a href="https://doi.org/${doi.textContent}" target="_blank" style="color:#BC0E0E;">${doi.textContent}</a></td></tr>`;
        }

        // PMC
        let pmcNodes = doc.evaluate("//idno[@type='PMCID']", doc, null, XPathResult.ANY_TYPE, null);
        let pmc = pmcNodes.iterateNext();
        if (pmc && pmc.textContent) {
            localHtml += `<tr><td>PMC ID</td><td><a href="https://www.ncbi.nlm.nih.gov/pmc/articles/${pmc.textContent}/" target="_blank" style="color:#BC0E0E;">${pmc.textContent}</a></td></tr>`;
        }

        // PMID
        let pmidNodes = doc.evaluate("//idno[@type='PMID']", doc, null, XPathResult.ANY_TYPE, null);
        let pmid = pmidNodes.iterateNext();
        if (pmid && pmid.textContent) {
            localHtml += `<tr><td>PMID</td><td><a href="https://pubmed.ncbi.nlm.nih.gov/${pmid.textContent}/" target="_blank" style="color:#BC0E0E;">${pmid.textContent}</a></td></tr>`;
        }

        // Open Access full text
        let oaNodes = doc.evaluate("//ptr[@type='open-access']/@target", doc, null, XPathResult.ANY_TYPE, null);
        let oa = oaNodes.iterateNext();
        if (oa && oa.textContent) {
            localHtml += `<tr><td>Open Access</td><td><a href="${oa.textContent}/" target="_blank" style="color:#BC0E0E;">${oa.textContent}</a></td></tr>`;
        }

        // publisher
        let publisherNodes = doc.evaluate("//publisher", doc, null, XPathResult.ANY_TYPE, null);
        let publisher = publisherNodes.iterateNext();
        if (publisher) {
            localHtml += `<tr><td>publisher</td><td>${publisher.textContent}</td></tr>`;
        }

        // editor
        let editorNodes = doc.evaluate("//editor", doc, null, XPathResult.ANY_TYPE, null);
        let editor = editorNodes.iterateNext();
        if (editor) {
            localHtml += `<tr><td>editor</td><td>${editor.textContent}</td></tr>`;
        }

        return localHtml;
    }

    // Utility methods
    isEmpty(element) {
        return !$.trim(element.html());
    }

    escapeHtml(text) {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // Basic XML parser for TEI references
    parse(xmlString) {
        try {
            const parser = new DOMParser();
            return parser.parseFromString(xmlString, 'text/xml');
        } catch (e) {
            return document.implementation.createDocument('', '', null);
        }
    }

    // Wikipedia image lookup with caching and JSONP (feature parity)
    lookupWikiMediaImage(wikipedia, lang, pageIndex) {
        const wikimediaURL_prefix = 'https://';
        const wikimediaURL_suffix = '.wikipedia.org/w/api.php?action=query&prop=pageimages&format=json&pithumbsize=200&pageids=';
        const supported = this.supportedLanguages || ['en'];
        if (!lang || supported.indexOf(lang) === -1) {
            lang = supported[0];
        }
        if (!this._wikimediaUrls) {
            this._wikimediaUrls = {};
            supported.forEach((l) => {
                this._wikimediaUrls[l] = wikimediaURL_prefix + l + wikimediaURL_suffix;
            });
        }
        if (!this._imgCache) this._imgCache = {};

        const cacheKey = lang + wikipedia;
        const spanNode = document.getElementById(`img-${wikipedia}-${pageIndex}`);
        if (!spanNode) return;

        if (this._imgCache[cacheKey]) {
            const imgUrl = this._imgCache[cacheKey];
            spanNode.innerHTML = `<img src="${imgUrl}" alt="Wikipedia thumbnail"/>`;
        } else {
            const theUrl = this._wikimediaUrls[lang] + wikipedia;
            $.ajax({
                url: theUrl,
                jsonp: 'callback',
                dataType: 'jsonp',
                xhrFields: { withCredentials: true },
                success: (response) => {
                    const span = document.getElementById(`img-${wikipedia}-${pageIndex}`);
                    if (response && response.query && response.query.pages && span && response.query.pages[wikipedia]) {
                        const pageInfo = response.query.pages[wikipedia];
                        if (pageInfo.thumbnail && pageInfo.thumbnail.source) {
                            const imgUrl = pageInfo.thumbnail.source;
                            span.innerHTML = `<img src="${imgUrl}" alt="Wikipedia thumbnail"/>`;
                            this._imgCache[cacheKey] = imgUrl;
                        }
                    }
                }
            });
        }
    }

    handleAjaxError(jqXHR) {
        const errorMsg = "Error encountered while requesting the server.<br/>" + jqXHR.responseText;
        $('#infoResult, #infoResult2').html(`<span style='color:red;'>${errorMsg}</span>`);
        this.entities = null;
    }

    handleAjaxError2(message = "") {
        const errorMsg = message + " - The PDF document cannot be annotated. Please check the server logs.";
        $('#infoResult, #infoResult2').html(`<span style='color:red;'>Error encountered while requesting the server.<br/>${errorMsg}</span>`);
        this.entities = null;
    }

    // Legacy compatibility method
    submitQuery() {
        // Redirect to modern methods
        if ("#divRestI" && $("#divRestI").is(":visible")) {
            this.submitTextQuery();
        } else if ("#divRestII" && $("#divRestII").is(":visible")) {
            this.submitPDFQuery();
        }
    }
}

// Global function for Wikipedia image lookup (called from HTML)
window.lookupWikiMediaImage = function(wikipedia, lang, pageIndex) {
    if (window.softciteApp) {
        window.softciteApp.lookupWikiMediaImage(wikipedia, lang, pageIndex);
    }
};

// Initialize the application when DOM is ready
$(document).ready(() => {
    window.softciteApp = new SoftciteApp();
    window.softciteApp.init();
});

// Legacy compatibility - keeping the old grobid object for any external dependencies
window.grobid = {
    // Expose key methods for backward compatibility
    submitQuery: () => window.softciteApp.submitQuery(),
    // Add other legacy methods as needed
};

