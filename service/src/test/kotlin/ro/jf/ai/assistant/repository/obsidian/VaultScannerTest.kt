package ro.jf.ai.assistant.repository.obsidian

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultScannerTest {
    private val scanner = VaultScanner()

    private fun file(
        path: String,
        content: String,
    ) = VaultFile(path, content.trimIndent())

    @Test
    fun `given a plain task in a topic file when scanning then the topic is inherited`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Family.md",
                        """
                        ---
                        topic: family
                        ---
                        ## Tasks
                        - [ ] Call grandma
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals("Call grandma", task.title)
        assertEquals("family", task.topic)
        assertEquals(false, task.done)
    }

    @Test
    fun `given an inline topic when scanning then it overrides the frontmatter topic`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Family.md",
                        """
                        ---
                        topic: family
                        ---
                        - [ ] Book appointment
                              [topic:: health]
                        """,
                    ),
                ),
            )

        assertEquals(
            "health",
            scan.tasks
                .single()
                .task.topic,
        )
    }

    @Test
    fun `given a cancelled task when scanning then it is done`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Family.md",
                        """
                        ---
                        topic: family
                        ---
                        - [-] Abandoned idea
                        """,
                    ),
                ),
            )

        assertTrue(
            scan.tasks
                .single()
                .task.done,
        )
    }

    @Test
    fun `given a recurrence template without a due date when scanning then it is excluded`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Water the plants
                              [recurrence:: every week]
                        - [ ] Water the plants
                              [due:: 2026-09-01]
                              [rid:: abc]
                        """,
                    ),
                ),
            )

        assertEquals(listOf(LocalDate.parse("2026-09-01")), scan.tasks.map { it.task.dueDate })
    }

    @Test
    fun `given a malformed due date when scanning then the task is listed with no due date`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Vague task
                              [due:: next tuesday]
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals("Vague task", task.title)
        assertNull(task.dueDate)
    }

    @Test
    fun `given a note without a frontmatter topic when scanning then its checkboxes are ignored`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "projects/Plan.md",
                        """
                        # Plan
                        - [ ] Do the research
                        - [ ] Write it up
                        """,
                    ),
                ),
            )

        assertTrue(scan.tasks.isEmpty())
    }

    @Test
    fun `given the inbox file when scanning then its tasks are listed without a topic`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "organization/Inbox.md",
                        """
                        # Inbox
                        - [ ] Something to sort later
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals("Something to sort later", task.title)
        assertNull(task.topic)
    }

    @Test
    fun `given a bold title when scanning then the emphasis markers are stripped`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] **Pay rent**
                        """,
                    ),
                ),
            )

        assertEquals(
            "Pay rent",
            scan.tasks
                .single()
                .task.title,
        )
    }

    @Test
    fun `given a title with multiple emphasis spans when scanning then only a whole wrapping span is stripped`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] *buy milk* and *eggs*
                        """,
                    ),
                ),
            )

        assertEquals(
            "*buy milk* and *eggs*",
            scan.tasks
                .single()
                .task.title,
        )
    }

    @Test
    fun `given an explicit id when scanning then it is used as the task id`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Pay rent
                              [id:: f3k2a1b2]
                        """,
                    ),
                ),
            )

        assertEquals(
            "f3k2a1b2",
            scan.tasks
                .single()
                .task.id,
        )
        assertEquals("Pay rent", scan.findById("f3k2a1b2")?.title)
    }

    @Test
    fun `given a task without an id when scanned twice then the derived id is stable`() {
        val files =
            listOf(
                file(
                    "areas/Home.md",
                    """
                    ---
                    topic: home
                    ---
                    - [ ] Pay rent
                    """,
                ),
            )

        val first =
            scanner
                .scan(files)
                .tasks
                .single()
                .task.id
        val second =
            scanner
                .scan(files)
                .tasks
                .single()
                .task.id

        assertEquals(first, second)
    }

    @Test
    fun `given two identical tasks in one file when scanning then their derived ids differ`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Pay rent
                        - [ ] Pay rent
                        """,
                    ),
                ),
            )

        val ids = scan.tasks.map { it.task.id }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `given a registry note when reading topics then the frontmatter list is returned`() {
        val topics =
            scanner.readTopics(
                listOf(
                    file(
                        "organization/Topics.md",
                        """
                        ---
                        topics: [family, home, work]
                        ---
                        # Topics
                        """,
                    ),
                ),
            )

        assertEquals(listOf("family", "home", "work"), topics)
    }

    @Test
    fun `given a block style registry note when reading topics then the list is returned`() {
        val topics =
            scanner.readTopics(
                listOf(
                    file(
                        "organization/Topics.md",
                        """
                        ---
                        topics:
                          - family
                          - home
                        ---
                        """,
                    ),
                ),
            )

        assertEquals(listOf("family", "home"), topics)
    }

    @Test
    fun `given an inline due on the checkbox line when scanning then the title and due are parsed`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "organization/Inbox.md",
                        """
                        # Inbox
                        - [ ] Pay rent [due:: 2026-09-01]
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals("Pay rent", task.title)
        assertEquals(LocalDate.parse("2026-09-01"), task.dueDate)
    }

    @Test
    fun `given an inline topic on the checkbox line when scanning then the title is clean of field expressions`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Family.md",
                        """
                        ---
                        topic: family
                        ---
                        - [ ] Book appointment [topic:: health]
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals("Book appointment", task.title)
        assertEquals("health", task.topic)
    }

    @Test
    fun `given a free-text note inside a block when scanning then the note belongs to the block`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        ## Tasks
                        - [ ] Plan trip
                              remember to check passports
                              [due:: 2026-10-01]
                        """,
                    ),
                ),
            )

        val scanned = scan.tasks.single()
        assertEquals("Plan trip", scanned.task.title)
        assertEquals(LocalDate.parse("2026-10-01"), scanned.task.dueDate)
        assertEquals(4, scanned.location.startLine)
        assertEquals(7, scanned.location.endLine)
    }

    @Test
    fun `given a field below a note line when scanning then the field is still parsed`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Call plumber
                              waiting on quote
                              [due:: 2026-09-20]
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals(LocalDate.parse("2026-09-20"), task.dueDate)
    }

    @Test
    fun `given a duplicate key on the checkbox line and a follow-up line when scanning then the checkbox line wins`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Home.md",
                        """
                        ---
                        topic: home
                        ---
                        - [ ] Pay rent [due:: 2026-09-01]
                              [due:: 2026-12-31]
                        """,
                    ),
                ),
            )

        val task = scan.tasks.single().task
        assertEquals(LocalDate.parse("2026-09-01"), task.dueDate)
    }

    @Test
    fun `given topic files when scanning then topic to file resolution is exposed`() {
        val scan =
            scanner.scan(
                listOf(
                    file(
                        "areas/Family.md",
                        """
                        ---
                        topic: family
                        ---
                        """,
                    ),
                ),
            )

        assertEquals(mapOf("family" to "areas/Family.md"), scan.topicToFile)
    }
}
