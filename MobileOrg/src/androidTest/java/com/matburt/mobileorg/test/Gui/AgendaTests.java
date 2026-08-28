package com.matburt.mobileorg.test.Gui;

import java.io.IOException;
import java.util.ArrayList;

import com.matburt.mobileorg.Gui.Agenda.OrgQueryBuilder;
import com.matburt.mobileorg.Gui.Agenda.OrgAgenda;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AgendaTests {

	private Context context;

	@Before
	public void setUp() throws Exception {
		this.context = InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	@Test
	public void testBlockSerialization() throws IOException {
		ArrayList<OrgAgenda> agendas = new ArrayList<OrgAgenda>();
		OrgAgenda blockAgenda = new OrgAgenda();
		blockAgenda.title = "test";
		agendas.add(blockAgenda);

		OrgAgenda.writeAgendas(agendas, context);
		ArrayList<OrgAgenda> readAgendas = OrgAgenda.readAgendas(context);

		assertEquals(agendas.size(), readAgendas.size());
		OrgAgenda readBlockAgenda = readAgendas.get(0);
		assertEquals(blockAgenda.title, readBlockAgenda.title);
	}

	@Test
	public void testQuerySerialization() throws IOException {
		ArrayList<OrgAgenda> agendas = new ArrayList<OrgAgenda>();
		OrgAgenda blockAgenda = new OrgAgenda();
		agendas.add(blockAgenda);

		OrgQueryBuilder builder = new OrgQueryBuilder("test");
		blockAgenda.queries.add(builder);

		OrgAgenda.writeAgendas(agendas, context);
		ArrayList<OrgAgenda> readAgendas = OrgAgenda.readAgendas(context);

		OrgAgenda readBlockAgenda = readAgendas.get(0);
		assertEquals(blockAgenda.queries.size(), readBlockAgenda.queries.size());
		assertEquals(blockAgenda.queries.get(0).title, readBlockAgenda.queries.get(0).title);
	}

	@Test
	public void testQueryPayloadFilterParameterizedAndEscaped() {
		OrgQueryBuilder builder = new OrgQueryBuilder("test");
		builder.payloads.add("100%");
		builder.payloads.add("it's");

		com.matburt.mobileorg.util.SelectionBuilder selection =
				builder.getQuery(context);

		String where = selection.getSelection();
		String[] args = selection.getSelectionArgs();

		org.junit.Assert.assertFalse(
				"filter values must not be concatenated into selection", where.contains("100%"));
		org.junit.Assert.assertFalse(where.contains("it's"));
		org.junit.Assert.assertTrue("LIKE clauses must declare ESCAPE",
				where.contains("ESCAPE '\\'"));
		// args[0] is the default agenda-file exclusion added by getFileSelection
		java.util.List<String> argList = java.util.Arrays.asList(args);
		assertEquals(3, args.length);
		org.junit.Assert.assertTrue(argList.contains("%100\\%%"));
		org.junit.Assert.assertTrue(argList.contains("%it's%"));
	}
}
