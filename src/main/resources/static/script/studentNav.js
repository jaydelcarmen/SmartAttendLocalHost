class StudentNav {
    constructor() {
        this.allCards      = [];
        this.filteredCards = [];
        this.currentPage   = 1;
        this.cardsPerPage  = 12;
    }

    init() {
        this.loadCards();
        this.loadProfile();

        document.getElementById('searchInput').addEventListener('keyup', () => {
            const val = document.getElementById('searchInput').value.toLowerCase();
            this.filteredCards = this.allCards.filter(c =>
                c.name.toLowerCase().includes(val) || c.subjectName.toLowerCase().includes(val)
            );
            this.currentPage = 1;
            this.renderCards();
            this.renderPagination();
        });

        document.getElementById('studentSectionForm').addEventListener('submit', e => {
            e.preventDefault();
            this.sectionForm();
        });

        document.addEventListener('click', e => {
            if (e.target.classList.contains('menu-btn')) {
                const menu = e.target.nextElementSibling;
                menu.style.display = menu.style.display === 'flex' ? 'none' : 'flex';
            } else {
                document.querySelectorAll('.menu-dropdown').forEach(m => m.style.display = 'none');
            }
        });
    }

    loadProfile() {
        fetch('/teacher/me')
            .then(r => r.json())
            .then(data => {
                document.getElementById('headerName').textContent = data.name || '';
                const img      = document.getElementById('headerAvatar');
                const initials = document.getElementById('headerInitials');
                if (data.profilePicture) {
                    img.src = data.profilePicture; img.style.display = 'block'; initials.style.display = 'none';
                } else {
                    img.style.display = 'none'; initials.style.display = 'block';
                    initials.textContent = (data.name || '?').charAt(0);
                }
            });
    }

    uploadProfilePicture(input) {
        const file = input.files[0];
        if (!file) return;
        const form = new FormData();
        form.append('file', file);
        fetch('/teacher/profile-picture', { method: 'POST', body: form })
            .then(r => r.text())
            .then(base64 => {
                const img      = document.getElementById('headerAvatar');
                const initials = document.getElementById('headerInitials');
                img.src = base64; img.style.display = 'block'; initials.style.display = 'none';
            });
    }

    openAddSectionModal()  { document.getElementById('addSectionModal').style.display = 'block'; }
    closeAddSectionModal() { document.getElementById('addSectionModal').style.display = 'none'; }

    toggleSidebar() {
        document.querySelector('.sidebar').classList.toggle('open');
        document.getElementById('hamburgerBtn').classList.toggle('open');
        document.getElementById('sidebarOverlay').classList.toggle('active');
    }

    async sectionForm() {
        const form        = document.getElementById('studentSectionForm');
        const sectionName = document.getElementById('SectionName').value;
        const subjectName = document.getElementById('SubjectName').value;
        const startTime   = document.getElementById('StartTime').value;
        const endTime     = document.getElementById('EndTime').value;
        const classDays   = [...document.querySelectorAll('.add-day-cb:checked')].map(c => c.value).join(',');

        if (!sectionName || !subjectName || !startTime || !endTime || !classDays)
            return Swal.fire({ icon: 'warning', title: 'Missing Fields', text: 'Please fill in all fields and select at least one class day' });

        try {
            const res = await fetch('/cards/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: sectionName, subjectName, startTime: startTime + ':00', endTime: endTime + ':00', classDays })
            });

            if (res.ok) {
                await Swal.fire({ icon: 'success', title: 'Card Added', text: 'Section created successfully', timer: 2000, showConfirmButton: false });
                document.getElementById('addSectionModal').style.display = 'none';
                form.reset();
                this.loadCards();
            } else {
                Swal.fire({ icon: 'error', title: 'Login Failed', text: await res.text() });
            }
        } catch (err) {
            console.error('Error adding card:', err);
        }
    }

    async loadCards() {
        try {
            const res      = await fetch('/cards/my-cards');
            this.allCards  = await res.json();
            this.filteredCards = [...this.allCards];
            this.renderCards();
            this.renderPagination();
        } catch (err) {
            console.error('Error loading cards:', err);
        }
    }

    renderCards() {
        const container = document.getElementById('cardsContainer');
        container.innerHTML = '';
        const start = (this.currentPage - 1) * this.cardsPerPage;
        const pageCards = this.filteredCards.slice(start, start + this.cardsPerPage);

        pageCards.forEach(card => {
            const el = document.createElement('div');
            el.classList.add('card-box');

            const fmt = t => t ? t.slice(0, 5).replace(/^(\d+):(\d+)/, (_, h, m) => {
                const hr = +h % 12 || 12;
                return `${hr}:${m} ${+h < 12 ? 'AM' : 'PM'}`;
            }) : '--';

            const daysHtml = card.classDays
                ? card.classDays.split(',').map(d => `<span class="day-chip">${d}</span>`).join('')
                : '<span class="day-chip day-chip--none">No days set</span>';

            el.innerHTML = `
                <div class="card-header">
                    <button class="menu-btn">⋮</button>
                    <div class="menu-dropdown">
                        <div onclick="studentNav.renameSection(${card.id}, '${card.name}')">Rename Section</div>
                        <div onclick="studentNav.renameSubject(${card.id}, '${card.subjectName}')">Rename Subject</div>
                        <div onclick="studentNav.setSchedule(${card.id})">Set Schedule</div>
                        <div onclick="studentNav.deleteCard(${card.id})">Delete</div>
                    </div>
                </div>
                <div class="card-body">
                    <span class="subject-name">${card.name}</span>
                    <p>${card.subjectName}</p>
                    <div class="card-time"><i class="fa-regular fa-clock"></i> ${fmt(card.startTime)} &ndash; ${fmt(card.endTime)}</div>
                    <div class="card-days">${daysHtml}</div>
                </div>`;

            el.querySelector('.card-body').addEventListener('click', () => {
                window.location.href = `/../body/studentTable.html?id=${card.id}`;
            });

            container.appendChild(el);
        });
    }

    renderPagination() {
        const pagination = document.querySelector('.pagination');
        pagination.innerHTML = '';
        const totalPages = Math.ceil(this.allCards.length / this.cardsPerPage);
        for (let i = 1; i <= totalPages; i++) {
            const btn = document.createElement('button');
            btn.classList.add('page-btn');
            btn.textContent = i;
            if (i === this.currentPage) btn.classList.add('active');
            btn.onclick = () => { this.currentPage = i; this.renderCards(); this.renderPagination(); };
            pagination.appendChild(btn);
        }
    }

    renameSection(id, currentName) {
        Swal.fire({ title: 'Rename Section', input: 'text', inputValue: currentName, showCancelButton: true })
            .then(result => {
                if (!result.isConfirmed) return;
                fetch(`/cards/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: result.value }) })
                    .then(r => r.json())
                    .then(() => Swal.fire({ icon: 'success', title: 'Rename Section', text: 'Section updated successfully', timer: 1500, showConfirmButton: false }).then(() => location.reload()));
            });
    }

    renameSubject(id, currentSubjectName) {
        Swal.fire({ title: 'Rename Subject', input: 'text', inputValue: currentSubjectName, showCancelButton: true })
            .then(result => {
                if (!result.isConfirmed) return;
                fetch(`/cards/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ subjectName: result.value }) })
                    .then(r => r.json())
                    .then(() => Swal.fire({ icon: 'success', title: 'Rename Subject', text: 'Subject updated successfully', timer: 1500, showConfirmButton: false }).then(() => location.reload()));
            });
    }

    setSchedule(id) {
        Swal.fire({
            title: 'Set Schedule',
            html: `<label>Start Time</label><br><input type="time" id="swal-start" class="swal2-input"><br>
                   <label>End Time</label><br><input type="time" id="swal-end" class="swal2-input"><br><br>
                   <label style="font-weight:600">Class Days</label><br>
                   <div style="display:flex;flex-wrap:wrap;gap:8px;justify-content:center;margin-top:8px">
                     ${['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(d =>
                       `<label style="display:flex;align-items:center;gap:4px;font-size:13px"><input type="checkbox" class="day-cb" value="${d}"> ${d}</label>`
                     ).join('')}
                   </div>`,
            showCancelButton: true,
            preConfirm: () => {
                const start = document.getElementById('swal-start').value;
                const end   = document.getElementById('swal-end').value;
                const days  = [...document.querySelectorAll('.day-cb:checked')].map(c => c.value).join(',');
                if (!start || !end) { Swal.showValidationMessage('Please fill in both times'); return false; }
                if (!days) { Swal.showValidationMessage('Please select at least one class day'); return false; }
                return { startTime: start + ':00', endTime: end + ':00', classDays: days };
            }
        }).then(result => {
            if (!result.isConfirmed) return;
            fetch(`/cards/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(result.value) })
                .then(r => r.json())
                .then(() => Swal.fire({ icon: 'success', title: 'Schedule Updated', timer: 1500, showConfirmButton: false }).then(() => this.loadCards()));
        });
    }

    deleteCard(id) {
        Swal.fire({ title: 'Delete this card?', text: 'This action cannot be undone.', icon: 'warning', showCancelButton: true, confirmButtonColor: '#d33', cancelButtonColor: '#3085d6', confirmButtonText: 'Yes, delete it' })
            .then(result => {
                if (!result.isConfirmed) return;
                fetch(`/cards/delete/${id}`, { method: 'DELETE' })
                    .then(res => {
                        if (res.ok) Swal.fire({ icon: 'success', title: 'Card has been deleted.', text: 'success', timer: 1000, showConfirmButton: false }).then(() => this.loadCards());
                        else return res.text().then(text => Swal.fire('Error', 'Could not delete card: ' + text, 'error'));
                    });
            });
    }
}

const studentNav = new StudentNav();
window.onload = () => studentNav.init();

// Global wrappers for HTML onclick attributes
function openAddSectionModal()         { studentNav.openAddSectionModal(); }
function closeAddSectionModal()        { studentNav.closeAddSectionModal(); }
function toggleSidebar()               { studentNav.toggleSidebar(); }
function uploadProfilePicture(input)   { studentNav.uploadProfilePicture(input); }
