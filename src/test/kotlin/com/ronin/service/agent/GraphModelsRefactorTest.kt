package com.ronin.service.agent

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.ronin.service.EditService
import com.ronin.service.EditService.EditOperation

class GraphModelsRefactorTest : BasePlatformTestCase() {

    fun `test graph models refactoring to mermaid only`() {
        val originalContent = """
# -*- coding: utf-8 -*-
import sys
import json
import os

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.template import loader

from extensions.management.modelviz import ModelGraph, generate_dot
from extensions.management.utils import signalcommand
import pydot

HAS_PYDOT = True


class Command(BaseCommand):
    help = "Creates a GraphViz dot file for the specified app names. You can pass multiple app names and they will all be combined into a single model. Output is usually directed to a dot file."

    can_import_settings = True
    
    # ... (truncated for brevity, simulate full content by targeted replacements)
    # Ideally we would put the full 371 lines here but for the test we focus on Key Areas.
    
    def __init__(self, *args, **kwargs):
        self.arguments = {
            "--pydot": { "action": "store_true", "default": True, "dest": "pydot", "help": "Output graph data as image using PyDot(Plus)." },
            "--dot": { "action": "store_true", "default": False, "dest": "dot", "help": "Output graph data as raw DOT." },
            "--json": { "action": "store_true", "default": False, "dest": "json", "help": "Output graph data as JSON" },
        }
        # ...

    @signalcommand
    def handle(self, *args, **options):
        # ...
        output_opts_names = ["pydot", "pygraphviz", "json", "dot"]
        # ...
        if output == "pydot":
            return self.render_output_pydot(dotdata, **options)
        self.print_output(dotdata, outputfile)

    def render_output_pydot(self, dotdata, **kwargs):
        if not HAS_PYDOT: raise CommandError("Need pydot")
        # ...

    def print_output(self, dotdata, output_file=None):
        # ...
""".trimIndent()

        val virtualFile = myFixture.configureByText("graph_models.py", originalContent).virtualFile
        val editService = com.intellij.openapi.components.ServiceManager.getService(project, EditService::class.java)

        // THE SOLUTION: Robust Edits to convert to Mermaid Only
        val edits = listOf(
            // 1. Remove Imports & Constants
            EditOperation(
                path = virtualFile.path,
                search = "import pydot\n\nHAS_PYDOT = True",
                replace = "# pydot removed for mermaid support"
            ),
            // 2. Update Arguments (Simplification)
            EditOperation(
                path = virtualFile.path,
                search = """        self.arguments = {
            "--pydot": { "action": "store_true", "default": True, "dest": "pydot", "help": "Output graph data as image using PyDot(Plus)." },
            "--dot": { "action": "store_true", "default": False, "dest": "dot", "help": "Output graph data as raw DOT." },
            "--json": { "action": "store_true", "default": False, "dest": "json", "help": "Output graph data as JSON" },
        }""",
                replace = """        self.arguments = {
            "--mermaid": { "action": "store_true", "default": True, "dest": "mermaid", "help": "Output graph data as Mermaid text." },
        }"""
            ),
            // 3. Update Logic in handle
            EditOperation(
                path = virtualFile.path,
                search = "output_opts_names = [\"pydot\", \"pygraphviz\", \"json\", \"dot\"]",
                replace = "output_opts_names = [\"mermaid\"]"
            ),
             EditOperation(
                path = virtualFile.path,
                search = """        if output == "pydot":
            return self.render_output_pydot(dotdata, **options)""",
                replace = """        if output == "mermaid":
            return self.print_output(dotdata, outputfile) # Mermaid text output"""
            ),
             // 4. Remove Deprecated Methods
             EditOperation(
                path = virtualFile.path,
                search = """    def render_output_pydot(self, dotdata, **kwargs):
        if not HAS_PYDOT: raise CommandError("Need pydot")
        # ...""",
                replace = ""
            )
        )

        // EXECUTE
        val results = editService.applyEdits(edits)

        // VERIFY
        val newContent = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)!!.text
        
        println("Results: \n" + results.joinToString("\n"))
        println("New Content: \n$newContent")

        assertTrue("Should contain mermaid argument", newContent.contains("--mermaid"))
        assertFalse("Should not contain pydot import", newContent.contains("import pydot"))
        assertFalse("Should not contain pydot option", newContent.contains("--pydot"))
        assertTrue("Should only have mermaid in opts names", newContent.contains("output_opts_names = [\"mermaid\"]"))
    }
}
