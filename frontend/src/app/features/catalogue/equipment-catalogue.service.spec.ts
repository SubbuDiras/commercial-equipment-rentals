import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EquipmentCatalogueService } from './equipment-catalogue.service';

describe('EquipmentCatalogueService', () => {
  let service: EquipmentCatalogueService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(EquipmentCatalogueService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only supplied search filters and pagination to the public catalogue', () => {
    let result: unknown;
    service.search({ categoryId: 'category-1', city: 'Bengaluru', page: 2, size: 12 }).subscribe(value => {
      result = value;
    });

    const request = http.expectOne(request =>
      request.url === '/api/v1/equipment' &&
      request.params.get('categoryId') === 'category-1' &&
      request.params.get('city') === 'Bengaluru' &&
      request.params.get('page') === '2' &&
      request.params.get('size') === '12' &&
      !request.params.has('minRate') &&
      !request.params.has('maxRate')
    );
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 2, size: 12, totalElements: 0, totalPages: 0 });
    expect(result).toEqual({ content: [], page: 2, size: 12, totalElements: 0, totalPages: 0 });
  });

  it('loads equipment details using an encoded listing identifier', () => {
    service.get('listing id').subscribe();

    const request = http.expectOne('/api/v1/equipment/listing%20id');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });
});
